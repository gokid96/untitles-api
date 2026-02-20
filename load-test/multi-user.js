import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ============================================================
//  다중 사용자 실사용 패턴 테스트
//
//  시드 유저 100명이 각자 자기 워크스페이스에서 노트 편집
//  posts 테이블 10만 건 상태에서 실제 운영 패턴 재현
//
//  "현실적인 시나리오에서도 동일한 결과가 나오는지" 검증
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'https://api.untitles.net';
const USER_COUNT = 100;
const PASSWORD = 'tjdals45!';

// ── 커스텀 메트릭 ──
const postUpdateDuration  = new Trend('post_update_duration', true);
const postGetDuration     = new Trend('post_get_duration', true);
const folderTreeDuration  = new Trend('folder_tree_duration', true);
const optimisticLockCount = new Counter('optimistic_lock_conflicts');

export const options = {
  setupTimeout: '300s',
  scenarios: {
    writers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m',  target: 55 },
        { duration: '3m',  target: 55 },
        { duration: '30s', target: 0  },
      ],
      exec: 'writerScenario',
    },
    readers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m',  target: 25 },
        { duration: '3m',  target: 25 },
        { duration: '30s', target: 0  },
      ],
      exec: 'readerScenario',
    },
  },
  thresholds: {
    http_req_duration:    ['p(95)<2000'],
    http_req_failed:      ['rate<0.05'],
    post_update_duration: ['p(95)<1000'],
    post_get_duration:    ['p(95)<500'],
    folder_tree_duration: ['p(95)<500'],
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
  const jar = http.cookieJar();

  for (let i = 1; i <= USER_COUNT; i++) {
    const loginId = `seeduser${String(i).padStart(4, '0')}`;

    // 이전 세션 제거 → 로그인 → jar에서 세션 추출
    jar.clear(`${BASE_URL}`);
    const loginRes = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ loginId, password: PASSWORD }),
      JSON_PARAMS
    );

    if (loginRes.status !== 200) {
      if (i <= 3) console.log(`로그인 실패 ${loginId}: ${loginRes.status}`);
      continue;
    }

    // jar에서 세션 쿠키 추출
    const cookies = jar.cookiesForURL(`${BASE_URL}`);
    let sessionCookie = '';
    if (cookies['JSESSIONID'] && cookies['JSESSIONID'].length > 0) {
      sessionCookie = 'JSESSIONID=' + cookies['JSESSIONID'][0];
    }
    if (!sessionCookie && loginRes.cookies['JSESSIONID']) {
      sessionCookie = 'JSESSIONID=' + loginRes.cookies['JSESSIONID'][0].value;
    }

    if (!sessionCookie) {
      if (i <= 3) console.log(`세션없음 ${loginId}`);
      continue;
    }

    const params = {
      headers: { 'Content-Type': 'application/json', 'Cookie': sessionCookie },
    };

    // 워크스페이스 목록
    jar.clear(`${BASE_URL}`);
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
      const rootPosts = data.rootPosts || [];
      if (Array.isArray(rootPosts)) {
        for (const post of rootPosts) {
          const pid = post.postId || post.id;
          if (pid) postIds.push(pid);
        }
      }
    } catch (e) {}

    users.push({ loginId, sessionCookie, workspaceId: wsId, postIds });

    if (i % 20 === 0) {
      console.log(`setup: ${i}/${USER_COUNT}명 (노트 ${postIds.length}개)`);
    }
    sleep(0.1);
  }

  console.log(`setup 완료: ${users.length}명, 총 노트 ${users.reduce((s, u) => s + u.postIds.length, 0)}개`);
  return { users };
}

function getMyUser(data) {
  if (!data.users || data.users.length === 0) return null;
  return data.users[(__VU - 1) % data.users.length];
}

// ============================================================
//  Writer 시나리오
// ============================================================
export function writerScenario(data) {
  const user = getMyUser(data);
  if (!user || user.postIds.length === 0) return;

  const params = {
    headers: { 'Content-Type': 'application/json', 'Cookie': user.sessionCookie },
  };
  const wsId = user.workspaceId;
  const targetPostId = randomPick(user.postIds);

  const treeRes = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
  folderTreeDuration.add(treeRes.timings.duration);
  check(treeRes, { '트리조회 200': (r) => r.status === 200 });
  sleep(0.5);

  const getRes = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`, params);
  postGetDuration.add(getRes.timings.duration);
  check(getRes, { '노트열기 200': (r) => r.status === 200 });
  sleep(0.5);

  const saveCount = 3 + Math.floor(Math.random() * 3);
  for (let j = 0; j < saveCount; j++) {
    const content = j < 2 ? SMALL_CONTENT : MEDIUM_CONTENT;
    const res = http.put(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
      JSON.stringify({ title: `edit-${user.loginId}-${__ITER}-${j}`, content }),
      params
    );
    postUpdateDuration.add(res.timings.duration);
    if (res.status === 409) optimisticLockCount.add(1);
    check(res, { '자동저장 성공': (r) => r.status === 200 || r.status === 409 });
    sleep(1 + Math.random());
  }

  const regetRes = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`, params);
  postGetDuration.add(regetRes.timings.duration);
  check(regetRes, { '노트재조회 200': (r) => r.status === 200 });
  sleep(0.3);

  const retreeRes = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
  folderTreeDuration.add(retreeRes.timings.duration);
  check(retreeRes, { '트리재조회 200': (r) => r.status === 200 });
  sleep(1);
}

// ============================================================
//  Reader 시나리오
// ============================================================
export function readerScenario(data) {
  const user = getMyUser(data);
  if (!user) return;

  const params = {
    headers: { 'Content-Type': 'application/json', 'Cookie': user.sessionCookie },
  };
  const wsId = user.workspaceId;

  http.get(`${BASE_URL}/api/v1/auth/me`, params);
  sleep(0.5);

  http.get(`${BASE_URL}/api/v1/workspaces`, params);
  sleep(0.5);

  const treeRes = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
  folderTreeDuration.add(treeRes.timings.duration);
  check(treeRes, { '트리조회 200': (r) => r.status === 200 });
  sleep(0.5);

  if (user.postIds.length > 0) {
    const getRes = http.get(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${randomPick(user.postIds)}`,
      params
    );
    postGetDuration.add(getRes.timings.duration);
    check(getRes, { '노트조회 200': (r) => r.status === 200 });
    sleep(0.5);
  }

  http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/members`, params);
  sleep(1 + Math.random());
}
