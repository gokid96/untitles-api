import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, HEADERS, TEST_USER } from './config.js';
import { randomString } from './helpers.js';

/**
 * ============================================
 * 3단계: Stress Test (스트레스 테스트)
 * ============================================
 * 목적: 시스템 한계점(Break Point) 찾기
 * 
 * 세션 전략: noCookiesReset + __ITER === 0 로그인
 * URL 그룹핑: tags.name으로 동적 URL을 그룹핑하여 메트릭 폭발 방지
 */

export const options = {
  noCookiesReset: true,
  stages: [
    { duration: '2m', target: 10 },
    { duration: '2m', target: 30 },
    { duration: '2m', target: 50 },
    { duration: '2m', target: 80 },
    { duration: '2m', target: 100 },
    { duration: '2m', target: 150 },
    { duration: '2m', target: 200 },
    { duration: '3m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.10'],
  },
};

function doLogin() {
  const jar = http.cookieJar();
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      loginId: TEST_USER.loginId,
      password: TEST_USER.password,
    }),
    { headers: HEADERS, jar: jar, tags: { name: 'POST /auth/login' } }
  );
  return res.status === 200;
}

export default function () {
  if (__ITER === 0) {
    if (!doLogin()) {
      console.error(`VU ${__VU}: 로그인 실패`);
      sleep(1);
      return;
    }
  }

  const jar = http.cookieJar();

  // ── 워크스페이스 조회 ──
  const wsRes = http.get(`${BASE_URL}/api/v1/workspaces`, {
    headers: HEADERS,
    jar: jar,
    tags: { name: 'GET /workspaces' },
  });

  if (wsRes.status === 401) {
    doLogin();
    sleep(0.3);
    return;
  }

  let workspaceId;
  if (wsRes.status === 200) {
    const body = JSON.parse(wsRes.body);
    const list = body.data || body;
    if (Array.isArray(list) && list.length > 0) {
      workspaceId = list[0].workspaceId;
    }
  }

  if (!workspaceId) {
    sleep(1);
    return;
  }

  sleep(0.3);

  // ── 폴더 트리 조회 ──
  http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}/folders`, {
    headers: HEADERS,
    jar: jar,
    tags: { name: 'GET /folders' },
  });

  sleep(0.3);

  // ── 게시글 생성 ──
  const createRes = http.post(
    `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts`,
    JSON.stringify({
      title: `stress ${randomString(5)}`,
      content: `<p>${randomString(100)}</p>`,
      folderId: null,
    }),
    { headers: HEADERS, jar: jar, tags: { name: 'POST /posts' } }
  );

  if (createRes.status === 200) {
    const body = JSON.parse(createRes.body);
    const postId = body.postId || (body.data && body.data.postId);
    if (postId) {
      sleep(0.2);

      // ── 게시글 수정 ──
      http.put(
        `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts/${postId}`,
        JSON.stringify({
          title: `stress updated ${randomString(5)}`,
          content: `<p>updated ${randomString(100)}</p>`,
        }),
        { headers: HEADERS, jar: jar, tags: { name: 'PUT /posts/{postId}' } }
      );

      sleep(0.2);

      // ── 게시글 삭제 ──
      http.del(
        `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts/${postId}`,
        null,
        { headers: HEADERS, jar: jar, tags: { name: 'DELETE /posts/{postId}' } }
      );
    }
  }

  sleep(0.5);
}
