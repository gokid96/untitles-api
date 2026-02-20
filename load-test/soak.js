import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ============================================================
//  untitles-api k6 Soak Test (CPU 크레딧 소진 상태)
//  t3.small standard 모드 → CPU 20% 베이스라인 제한
//  VU 50명으로 30분 유지 → 성능 저하 관찰
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'https://api.untitles.net';
const WORKSPACE_ID = __ENV.WORKSPACE_ID || '6';

const TEST_USER = {
  loginId: __ENV.LOGIN_ID || 'admin',
  password: __ENV.PASSWORD || 'tjdals45!',
};

const SEED_FOLDERS = 3;
const SEED_POSTS   = 15;

// ── 커스텀 메트릭 ──
const postUpdateDuration = new Trend('post_update_duration', true);
const postGetDuration    = new Trend('post_get_duration', true);
const folderTreeDuration = new Trend('folder_tree_duration', true);
const errorCount         = new Counter('business_errors');
const optimisticLockCount = new Counter('optimistic_lock_conflicts');

export const options = {
  stages: [
    { duration: '1m',  target: 150 },   // ramp-up
    { duration: '28m', target: 150 },   // 28분 유지
    { duration: '1m',  target: 0   },   // ramp-down
  ],
  thresholds: {
    http_req_duration:    ['p(95)<10000'],  // 느려질 수 있으니 여유있게
    http_req_failed:      ['rate<0.10'],
    post_update_duration: ['p(95)<10000'],
    post_get_duration:    ['p(95)<5000'],
    folder_tree_duration: ['p(95)<5000'],
  },
};

const JSON_PARAMS = { headers: { 'Content-Type': 'application/json' } };

function generateNoteContent(sizeKB) {
  const paragraph = '<p>이것은 테스트용 노트 내용입니다. 실제 사용자가 작성하는 것처럼 적당한 분량의 텍스트를 포함합니다. </p>';
  const repeat = Math.ceil((sizeKB * 1024) / paragraph.length);
  return paragraph.repeat(repeat).substring(0, sizeKB * 1024);
}

const SMALL_CONTENT  = generateNoteContent(1);
const MEDIUM_CONTENT = generateNoteContent(5);

function randomPick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify(TEST_USER),
    JSON_PARAMS
  );
  check(loginRes, { 'setup 로그인 200': (r) => r.status === 200 });

  const setCookie = loginRes.headers['Set-Cookie'] || '';
  const match = setCookie.match(/(?:SESSION|JSESSIONID)=([^;]+)/);
  const sessionCookie = match ? match[0] : '';

  const params = {
    headers: { 'Content-Type': 'application/json', 'Cookie': sessionCookie },
  };

  const wsId = WORKSPACE_ID;

  // 시드 폴더 생성
  const folderIds = [];
  for (let i = 0; i < SEED_FOLDERS; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/workspaces/${wsId}/folders`,
      JSON.stringify({ name: `k6-soak-folder-${i}-${Date.now()}`, parentId: null, orderIndex: i }),
      params
    );
    if (res.status === 201 || res.status === 200) {
      try {
        const body = res.json();
        const id = (body.data || body).folderId || (body.data || body).id;
        if (id) folderIds.push(id);
      } catch (e) {}
    }
    sleep(0.1);
  }

  // 시드 노트 생성
  const postIds = [];
  for (let i = 0; i < SEED_POSTS; i++) {
    const folderId = folderIds.length > 0 ? folderIds[i % folderIds.length] : null;
    const res = http.post(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts`,
      JSON.stringify({ title: `k6-soak-note-${i}`, content: SMALL_CONTENT, folderId }),
      params
    );
    if (res.status === 200 || res.status === 201) {
      try {
        const body = res.json();
        const id = (body.data || body).postId || (body.data || body).id;
        if (id) postIds.push(id);
      } catch (e) {}
    }
    sleep(0.1);
  }

  console.log(`시드 데이터: 폴더 ${folderIds.length}개, 노트 ${postIds.length}개`);
  return { sessionCookie, workspaceId: wsId, folderIds, postIds };
}

function authParams(data) {
  return {
    headers: { 'Content-Type': 'application/json', 'Cookie': data.sessionCookie },
  };
}

export default function (data) {
  if (!data.postIds || data.postIds.length === 0) return;

  const params = authParams(data);
  const wsId = data.workspaceId;
  const targetPostId = randomPick(data.postIds);

  // 1. 트리 조회
  group('01_트리조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
    folderTreeDuration.add(res.timings.duration);
    check(res, { '트리조회 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 2. 노트 열기
  group('02_노트열기', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`, params);
    postGetDuration.add(res.timings.duration);
    check(res, { '노트열기 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 3. 자동저장 (PUT 3회)
  for (let i = 0; i < 3; i++) {
    group('03_자동저장', () => {
      const content = i < 2 ? SMALL_CONTENT : MEDIUM_CONTENT;
      const payload = JSON.stringify({
        title: `soak-edit-${__VU}-${__ITER}-${i}`,
        content: content,
      });
      const res = http.put(
        `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
        payload,
        params
      );
      postUpdateDuration.add(res.timings.duration);
      if (res.status === 409) optimisticLockCount.add(1);
      const ok = check(res, { '자동저장 성공': (r) => r.status === 200 || r.status === 409 });
      if (!ok) errorCount.add(1);
    });
    sleep(1 + Math.random());
  }

  // 4. 노트 재조회
  group('04_노트재조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`, params);
    postGetDuration.add(res.timings.duration);
    check(res, { '노트재조회 200': (r) => r.status === 200 });
  });

  sleep(1);
}

export function teardown(data) {
  if (!data.sessionCookie) return;
  const params = {
    headers: { 'Content-Type': 'application/json', 'Cookie': data.sessionCookie },
  };
  const wsId = data.workspaceId;

  if (data.postIds) {
    for (const id of data.postIds) {
      http.del(`${BASE_URL}/api/v1/workspaces/${wsId}/posts/${id}`, null, params);
      sleep(0.05);
    }
    console.log(`시드 노트 ${data.postIds.length}개 삭제`);
  }
  if (data.folderIds) {
    for (const id of data.folderIds) {
      http.del(`${BASE_URL}/api/v1/workspaces/${wsId}/folders/${id}`, null, params);
      sleep(0.05);
    }
    console.log(`시드 폴더 ${data.folderIds.length}개 삭제`);
  }
}
