import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { BASE_URL, HEADERS, TEST_USER, DEFAULT_THRESHOLDS } from './config.js';
import { randomString } from './helpers.js';

/**
 * ============================================
 * 2단계: Load Test (부하 테스트)
 * ============================================
 * 목적: 예상 트래픽 수준에서 성능 확인
 * 실행: k6 run k6/02-load.js -e BASE_URL=https://your-api.com
 * 
 * 시나리오: 실제 유저 행동 시뮬레이션
 *   로그인 → 워크스페이스 조회 → 트리 조회 → 게시글 CRUD
 */

export const options = {
  stages: [
    { duration: '1m', target: 10 },   // 1분간 10명으로 증가 (워밍업)
    { duration: '3m', target: 10 },   // 3분간 10명 유지 (안정 부하)
    { duration: '1m', target: 20 },   // 1분간 20명으로 증가
    { duration: '3m', target: 20 },   // 3분간 20명 유지
    { duration: '1m', target: 0 },    // 1분간 0명으로 감소 (쿨다운)
  ],
  thresholds: DEFAULT_THRESHOLDS,
};

export default function () {
  const jar = http.cookieJar();

  // ── 1. 로그인 ──
  group('로그인', () => {
    const res = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({
        loginId: TEST_USER.loginId,
        password: TEST_USER.password,
      }),
      { headers: HEADERS, jar: jar }
    );
    check(res, { '로그인 200': (r) => r.status === 200 });
  });

  sleep(1);

  // ── 2. 워크스페이스 목록 조회 ──
  let workspaceId;
  group('워크스페이스 목록', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces`, {
      headers: HEADERS,
      jar: jar,
    });
    check(res, { '워크스페이스 목록 200': (r) => r.status === 200 });

    const body = JSON.parse(res.body);
    const list = body.data || body;
    if (Array.isArray(list) && list.length > 0) {
      workspaceId = list[0].workspaceId;
    }
  });

  if (!workspaceId) {
    sleep(1);
    return; // 워크스페이스가 없으면 여기서 끝
  }

  sleep(0.5);

  // ── 3. 폴더 트리 조회 (가장 자주 호출되는 API) ──
  group('폴더 트리 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/workspaces/${workspaceId}/folders`,
      { headers: HEADERS, jar: jar }
    );
    check(res, { '트리 조회 200': (r) => r.status === 200 });
  });

  sleep(0.5);

  // ── 4. 게시글 생성 ──
  let postId;
  group('게시글 생성', () => {
    const res = http.post(
      `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts`,
      JSON.stringify({
        title: `k6 테스트 게시글 ${randomString(6)}`,
        content: `<p>부하 테스트 본문 ${randomString(20)}</p>`,
        folderId: null,
      }),
      { headers: HEADERS, jar: jar }
    );
    check(res, { '게시글 생성 200': (r) => r.status === 200 });

    if (res.status === 200) {
      const body = JSON.parse(res.body);
      postId = body.postId || (body.data && body.data.postId);
    }
  });

  sleep(0.5);

  // ── 5. 게시글 조회 ──
  if (postId) {
    group('게시글 조회', () => {
      const res = http.get(
        `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts/${postId}`,
        { headers: HEADERS, jar: jar }
      );
      check(res, { '게시글 조회 200': (r) => r.status === 200 });
    });

    sleep(0.5);

    // ── 6. 게시글 수정 ──
    group('게시글 수정', () => {
      const res = http.put(
        `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts/${postId}`,
        JSON.stringify({
          title: `k6 수정됨 ${randomString(6)}`,
          content: `<p>수정된 본문 ${randomString(30)}</p>`,
        }),
        { headers: HEADERS, jar: jar }
      );
      check(res, { '게시글 수정 200': (r) => r.status === 200 });
    });

    sleep(0.5);

    // ── 7. 게시글 삭제 (테스트 데이터 정리) ──
    group('게시글 삭제', () => {
      const res = http.del(
        `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts/${postId}`,
        null,
        { headers: HEADERS, jar: jar }
      );
      check(res, { '게시글 삭제 204': (r) => r.status === 204 });
    });
  }

  sleep(1);
}
