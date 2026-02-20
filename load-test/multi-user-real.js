import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ============================================================
//  untitles-api 다중 사용자 실사용 패턴 테스트
//
//  시드 유저 100명이 각자 자기 워크스페이스에서 노트 편집
//  posts 테이블 10만 건 상태에서 실제 운영 패턴 재현
//
//  setup(): 100명 로그인 → 세션 + 워크스페이스ID + 노트ID 수집
//  default: VU마다 자기 계정으로 노트 조회/수정
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'https://api.untitles.net';
const USER_COUNT = 100; // 로그인할 시드 유저 수
const PASSWORD = 'tjdals45!';

// ── 커스텀 메트릭 ──
const postUpdateDuration = new Trend('post_update_duration', true);
const postGetDuration    = new Trend('post_get_duration', true);
const folderTreeDuration = new Trend('folder_tree_duration', true);
const errorCount         = new Counter('business_errors');
const optimisticLockCount = new Counter('optimistic_lock_conflicts');

export const options = {
  scenarios: {
    writers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 30  },
        { duration: '2m',  target: 50  },
        { duration: '1m',  target: 100 },
        { duration: '2m',  target: 100 },
        { duration: '30s', target: 0   },
      ],
      exec: 'writerScenario',
    },
    readers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20  },
        { duration: '2m',  target: 30  },
        { duration: '1m',  target: 50  },
        { duration: '2m',  target: 50  },
        { duration: '30s', target: 0   },
      ],
      exec: 'readerScenario',
    },
  },
  thresholds: {
    http_req_duration:     ['p(95)<3000'],
    http_req_failed:       ['rate<0.05'],
    post_update_duration:  ['p(95)<2000'],
    post_get_duration:     ['p(95)<1000'],
    folder_tree_duration:  ['p(95)<1500'],
    optimistic_lock_conflicts: ['count<500'],
  },
};

const JSON_PARAMS = { headers: { 'Content-Type': 'application/json' } };

function generateContent(size) {
  const base = '<p>이것은 테스트용 노트 내용입니다. 실제 사용자가 작성하는 것처럼 적당한 분량의 텍스트를 포함합니다. </p>';
  return base.repeat(Math.ceil(size / base.length)).substring(0, size);
}

const SMALL_CONTENT  = generateContent(1024);
const MEDIUM_CONTENT = generateContent(5120);

function randomPick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// ============================================================
//  setup: 시드 유저 100명 로그인 + 워크스페이스/노트 ID 수집
// ============================================================
export function setup() {
  const users = [];

  for (let i = 1; i <= USER_COUNT; i++) {
    const loginId = `seeduser${String(i).padStart(4, '0')}`;

    // 로그인
    const loginRes = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ loginId, password: PASSWORD }),
      JSON_PARAMS
    );

    if (loginRes.status !== 200) {
      if (i <= 3) console.log(`로그인 실패 ${loginId}: ${loginRes.status}`);
      continue;
    }

    const setCookie = loginRes.headers['Set-Cookie'] || '';
    const match = setCookie.match(/(?:SESSION|JSESSIONID)=([^;]+)/);
    const sessionCookie = match ? match[0] : '';
    if (!sessionCookie) continue;

    const params = {
      headers: { 'Content-Type': 'application/json', 'Cookie': sessionCookie },
    };

    // 워크스페이스 목록
    const wsRes = http.get(`${BASE_URL}/api/v1/workspaces`, params);
    let wsId = null;
    try {
      const body = wsRes.json();
      const list = body.data || body;
      if (Array.isArray(list) && list.length > 0) {
        wsId = list[0].workspaceId || list[0].id;
      }
    } catch (e) {}

    if (!wsId) continue;

    // 폴더 트리에서 노트 ID 수집
    const treeRes = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
    const postIds = [];
    try {
      const body = treeRes.json();
      const data = body.data || body;

      // 폴더 내 노트
      const folders = data.folders || data;
      if (Array.isArray(folders)) {
        for (const folder of folders) {
          if (folder.posts && Array.isArray(folder.posts)) {
            for (const post of folder.posts) {
              const pid = post.postId || post.id;
              if (pid) postIds.push(pid);
            }
          }
        }
      }

      // 루트 노트
      const rootPosts = data.rootPosts || [];
      if (Array.isArray(rootPosts)) {
        for (const post of rootPosts) {
          const pid = post.postId || post.id;
          if (pid) postIds.push(pid);
        }
      }
    } catch (e) {}

    users.push({
      loginId,
      sessionCookie,
      workspaceId: wsId,
      postIds,
    });

    if (i % 20 === 0) {
      console.log(`setup 진행: ${i}/${USER_COUNT}명 (수집 노트: ${postIds.length}개)`);
    }

    sleep(0.1);
  }

  console.log(`setup 완료: ${users.length}명 로그인, 총 노트 ${users.reduce((s, u) => s + u.postIds.length, 0)}개`);
  return { users };
}

// ── VU → 사용자 매핑 ──
function getMyUser(data) {
  if (!data.users || data.users.length === 0) return null;
  const idx = (__VU - 1) % data.users.length;
  return data.users[idx];
}

function authParams(user) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'Cookie': user.sessionCookie,
    },
  };
}

// ============================================================
//  Writer 시나리오: 자기 워크스페이스에서 노트 수정
// ============================================================
export function writerScenario(data) {
  const user = getMyUser(data);
  if (!user || user.postIds.length === 0) return;

  const params = authParams(user);
  const wsId = user.workspaceId;
  const targetPostId = randomPick(user.postIds);

  // 1. 트리 조회
  group('writer_01_트리조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
    folderTreeDuration.add(res.timings.duration);
    check(res, { '트리조회 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 2. 노트 열기
  group('writer_02_노트열기', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
      params
    );
    postGetDuration.add(res.timings.duration);
    check(res, { '노트열기 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 3. 자동저장 (PUT 3~5회)
  const autoSaveCount = 3 + Math.floor(Math.random() * 3);
  for (let i = 0; i < autoSaveCount; i++) {
    group('writer_03_자동저장', () => {
      const content = i < 2 ? SMALL_CONTENT : MEDIUM_CONTENT;
      const payload = JSON.stringify({
        title: `edited-${user.loginId}-${__ITER}-save${i + 1}`,
        content: content,
      });
      const res = http.put(
        `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
        payload,
        params
      );
      postUpdateDuration.add(res.timings.duration);
      if (res.status === 409) optimisticLockCount.add(1);
      const ok = check(res, {
        '자동저장 성공': (r) => r.status === 200 || r.status === 409,
      });
      if (!ok) errorCount.add(1);
    });
    sleep(1 + Math.random());
  }

  // 4. 노트 재조회
  group('writer_04_노트재조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
      params
    );
    postGetDuration.add(res.timings.duration);
    check(res, { '노트재조회 200': (r) => r.status === 200 });
  });
  sleep(0.3);

  // 5. 트리 재조회
  group('writer_05_트리재조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
    folderTreeDuration.add(res.timings.duration);
    check(res, { '트리재조회 200': (r) => r.status === 200 });
  });

  sleep(1);
}

// ============================================================
//  Reader 시나리오: 자기 워크스페이스 조회
// ============================================================
export function readerScenario(data) {
  const user = getMyUser(data);
  if (!user) return;

  const params = authParams(user);
  const wsId = user.workspaceId;

  // 1. 내 정보
  group('reader_01_내정보', () => {
    const res = http.get(`${BASE_URL}/api/v1/auth/me`, params);
    check(res, { '내정보 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 2. 워크스페이스 목록
  group('reader_02_워크스페이스목록', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces`, params);
    check(res, { '워크스페이스목록 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 3. 트리 조회
  group('reader_03_트리조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
    folderTreeDuration.add(res.timings.duration);
    check(res, { '트리조회 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 4. 노트 조회
  if (user.postIds.length > 0) {
    const targetPostId = randomPick(user.postIds);
    group('reader_04_노트조회', () => {
      const res = http.get(
        `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
        params
      );
      postGetDuration.add(res.timings.duration);
      check(res, { '노트조회 200': (r) => r.status === 200 });
    });
    sleep(0.5);
  }

  // 5. 멤버 목록
  group('reader_05_멤버목록', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/members`, params);
    check(res, { '멤버목록 200': (r) => r.status === 200 });
  });

  sleep(1 + Math.random());
}
