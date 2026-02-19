import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ============================================================
//  untitles-api k6 Load Test
//  실제 사용자 시나리오 시뮬레이션 (CRUD 포함)
// ============================================================

const BASE_URL = 'https://api.untitles.net';

const TEST_USER = {
  loginId: 'admin',
  password: 'tjdals45!',
};

export const options = {
  stages: [
    { duration: '1m', target: 10 },   // 10명까지 증가
    { duration: '3m', target: 10 },   // 10명 유지
    { duration: '1m', target: 20 },   // 20명까지 증가
    { duration: '3m', target: 20 },   // 20명 유지
    { duration: '1m', target: 0 },    // 종료
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500'],
    http_req_failed: ['rate<0.05'],
  },
};

const PARAMS = { headers: { 'Content-Type': 'application/json' } };

export default function () {

  // 1. 로그인
  group('01_로그인', () => {
    const res = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify(TEST_USER),
      PARAMS
    );
    check(res, { '로그인 200': (r) => r.status === 200 });
  });
  sleep(1);

  // 2. 내 정보 조회
  group('02_내정보_조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/auth/me`, PARAMS);
    check(res, { '내정보 200': (r) => r.status === 200 });
  });
  sleep(1);

  // 3. 워크스페이스 목록
  let workspaceId;
  group('03_워크스페이스_목록', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces`, PARAMS);
    check(res, { '워크스페이스목록 200': (r) => r.status === 200 });
    const body = res.json();
    const list = body.data || body;
    if (Array.isArray(list) && list.length > 0) {
      workspaceId = list[0].workspaceId || list[0].id;
    }
  });
  sleep(1);

  if (workspaceId) {
    // 4. 폴더 트리 조회
    group('04_폴더트리_조회', () => {
      const res = http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}/folders`, PARAMS);
      check(res, { '폴더트리 200': (r) => r.status === 200 });
    });
    sleep(1);

    // 5. 게시글 생성
    let postId;
    group('05_게시글_생성', () => {
      const payload = JSON.stringify({
        title: 'k6 load test ' + Date.now(),
        content: '<p>k6 부하 테스트 내용</p>',
        folderId: null,
      });
      const res = http.post(
        `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts`,
        payload,
        PARAMS
      );
      check(res, { '게시글생성 200': (r) => r.status === 200 });
      const body = res.json();
      postId = body.data ? body.data.postId : body.postId;
    });
    sleep(1);

    // 6. 게시글 조회
    if (postId) {
      group('06_게시글_조회', () => {
        const res = http.get(
          `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts/${postId}`,
          PARAMS
        );
        check(res, { '게시글조회 200': (r) => r.status === 200 });
      });
      sleep(1);

      // 7. 게시글 삭제
      group('07_게시글_삭제', () => {
        const res = http.del(
          `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts/${postId}`,
          null,
          PARAMS
        );
        check(res, { '게시글삭제 204': (r) => r.status === 204 });
      });
      sleep(0.5);
    }
  }

  // 8. 로그아웃
  group('08_로그아웃', () => {
    const res = http.post(`${BASE_URL}/api/v1/auth/logout`, null, PARAMS);
    check(res, { '로그아웃 200': (r) => r.status === 200 });
  });
  sleep(1);
}
