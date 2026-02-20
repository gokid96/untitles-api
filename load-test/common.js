import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ============================================================
//  untitles-api 공통 모듈
//
//  모든 테스트에서 공유하는 로그인, 시나리오, 헬퍼 함수
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'https://api.untitles.net';

const TEST_USER = {
  loginId: __ENV.LOGIN_ID || 'admin',
  password: __ENV.PASSWORD || 'tjdals45!',
};

// ── 커스텀 메트릭 ──
export const postUpdateDuration  = new Trend('post_update_duration', true);
export const postGetDuration     = new Trend('post_get_duration', true);
export const folderTreeDuration  = new Trend('folder_tree_duration', true);
export const optimisticLockCount = new Counter('optimistic_lock_conflicts');

const JSON_PARAMS = { headers: { 'Content-Type': 'application/json' } };

// ── 컨텐츠 생성 ──
function generateContent(size) {
  const base = '<p>이것은 테스트용 노트 내용입니다. 실제 사용자가 작성하는 것처럼 적당한 분량의 텍스트를 포함합니다. </p>';
  return base.repeat(Math.ceil(size / base.length)).substring(0, size);
}

export const SMALL_CONTENT  = generateContent(1024);
export const MEDIUM_CONTENT = generateContent(5120);

export function randomPick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// ============================================================
//  로그인 + 시드 데이터 생성 (setup용)
// ============================================================
export function loginAndSeed(folderCount, postCount) {
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify(TEST_USER),
    JSON_PARAMS
  );
  check(loginRes, { 'setup 로그인 200': (r) => r.status === 200 });

  // 세션 쿠키 추출
  // 세션 쿠키 추출 (res.cookies 사용)
  let sessionCookie = '';
  if (loginRes.cookies['JSESSIONID'] && loginRes.cookies['JSESSIONID'].length > 0) {
    sessionCookie = 'JSESSIONID=' + loginRes.cookies['JSESSIONID'][0].value;
  }
  if (!sessionCookie) {
    const jar = http.cookieJar();
    const cookies = jar.cookiesForURL(`${BASE_URL}`);
    if (cookies['JSESSIONID'] && cookies['JSESSIONID'].length > 0) {
      sessionCookie = 'JSESSIONID=' + cookies['JSESSIONID'][0];
    }
  }
  console.log(`세션쿠키: ${sessionCookie ? '성공' : '실패'}`);

  const params = {
    headers: { 'Content-Type': 'application/json', 'Cookie': sessionCookie },
  };

  // 워크스페이스 ID 가져오기
  const wsRes = http.get(`${BASE_URL}/api/v1/workspaces`, params);
  console.log(`워크스페이스 조회: status=${wsRes.status}, body=${wsRes.body.substring(0, 200)}`);
  let wsList;
  try {
    wsList = wsRes.json();
  } catch (e) {
    console.log(`워크스페이스 JSON 파싱 실패`);
    return { sessionCookie, wsId: null, folderIds: [], postIds: [] };
  }
  const list = wsList.data || wsList;
  const first = Array.isArray(list) ? list[0] : list;
  const wsId = first.workspaceId || first.id;
  console.log(`워크스페이스 ID: ${wsId}`);

  // 시드 폴더 생성
  const folderIds = [];
  for (let i = 0; i < folderCount; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/workspaces/${wsId}/folders`,
      JSON.stringify({ name: `k6-folder-${Date.now()}-${i}`, parentId: null }),
      params
    );
    if (res.status === 200 || res.status === 201) {
      try {
        const body = res.json();
        const d = body.data || body;
        folderIds.push(d.folderId || d.id);
      } catch (e) {}
    }
    sleep(0.05);
  }

  // 시드 노트 생성
  const postIds = [];
  for (let i = 0; i < postCount; i++) {
    const folderId = folderIds.length > 0 ? folderIds[i % folderIds.length] : null;
    const res = http.post(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts`,
      JSON.stringify({
        title: `k6-note-${Date.now()}-${i}`,
        content: generateContent(500 + Math.floor(Math.random() * 1500)),
        folderId,
      }),
      params
    );
    if (res.status === 200 || res.status === 201) {
      try {
        const body = res.json();
        const d = body.data || body;
        postIds.push(d.postId || d.id);
      } catch (e) {}
    }
    sleep(0.05);
  }

  console.log(`시드 데이터: 폴더 ${folderIds.length}개, 노트 ${postIds.length}개`);

  return { sessionCookie, wsId, folderIds, postIds };
}

// ============================================================
//  시드 데이터 정리 (teardown용)
// ============================================================
export function cleanupSeed(data) {
  const params = {
    headers: { 'Content-Type': 'application/json', 'Cookie': data.sessionCookie },
  };

  // 노트 삭제
  for (const postId of data.postIds) {
    http.del(`${BASE_URL}/api/v1/workspaces/${data.wsId}/posts/${postId}`, null, params);
    sleep(0.02);
  }
  console.log(`시드 노트 ${data.postIds.length}개 삭제`);

  // 폴더 삭제
  for (const folderId of data.folderIds) {
    http.del(`${BASE_URL}/api/v1/workspaces/${data.wsId}/folders/${folderId}`, null, params);
    sleep(0.02);
  }
  console.log(`시드 폴더 ${data.folderIds.length}개 삭제`);
}

// ============================================================
//  Writer 시나리오: 트리조회 → 노트열기 → 자동저장 → 노트재조회
// ============================================================
export function writerScenario(data) {
  if (!data.postIds || data.postIds.length === 0) return;

  const params = {
    headers: { 'Content-Type': 'application/json', 'Cookie': data.sessionCookie },
  };
  const wsId = data.wsId;
  const targetPostId = randomPick(data.postIds);

  // 1. 트리 조회
  const treeRes = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
  folderTreeDuration.add(treeRes.timings.duration);
  check(treeRes, { '트리조회 200': (r) => r.status === 200 });
  sleep(0.5);

  // 2. 노트 열기
  const getRes = http.get(
    `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
    params
  );
  postGetDuration.add(getRes.timings.duration);
  check(getRes, { '노트열기 200': (r) => r.status === 200 });
  sleep(0.5);

  // 3. 자동저장 (PUT 3~5회, 1~2초 간격)
  const saveCount = 3 + Math.floor(Math.random() * 3);
  for (let i = 0; i < saveCount; i++) {
    const content = i < 2 ? SMALL_CONTENT : MEDIUM_CONTENT;
    const res = http.put(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
      JSON.stringify({
        title: `edited-${__VU}-${__ITER}-${i}`,
        content,
      }),
      params
    );
    postUpdateDuration.add(res.timings.duration);
    if (res.status === 409) optimisticLockCount.add(1);
    check(res, { '자동저장 성공': (r) => r.status === 200 || r.status === 409 });
    sleep(1 + Math.random());
  }

  // 4. 노트 재조회
  const regetRes = http.get(
    `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
    params
  );
  postGetDuration.add(regetRes.timings.duration);
  check(regetRes, { '노트재조회 200': (r) => r.status === 200 });
  sleep(0.3);

  // 5. 트리 재조회
  const retreeRes = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
  folderTreeDuration.add(retreeRes.timings.duration);
  check(retreeRes, { '트리재조회 200': (r) => r.status === 200 });

  sleep(1);
}

// ============================================================
//  Reader 시나리오: 내정보 → 워크스페이스 → 트리 → 노트 → 멤버
// ============================================================
export function readerScenario(data) {
  const params = {
    headers: { 'Content-Type': 'application/json', 'Cookie': data.sessionCookie },
  };
  const wsId = data.wsId;

  http.get(`${BASE_URL}/api/v1/auth/me`, params);
  sleep(0.5);

  http.get(`${BASE_URL}/api/v1/workspaces`, params);
  sleep(0.5);

  const treeRes = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
  folderTreeDuration.add(treeRes.timings.duration);
  check(treeRes, { '트리조회 200': (r) => r.status === 200 });
  sleep(0.5);

  if (data.postIds && data.postIds.length > 0) {
    const getRes = http.get(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${randomPick(data.postIds)}`,
      params
    );
    postGetDuration.add(getRes.timings.duration);
    check(getRes, { '노트조회 200': (r) => r.status === 200 });
    sleep(0.5);
  }

  http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/members`, params);
  sleep(1 + Math.random());
}
