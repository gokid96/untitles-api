import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ============================================================
//  untitles-api k6 Load Test
//  VU 10→50명 — 예상 트래픽에서 응답시간, 에러율 확인
// ============================================================

const BASE_URL = 'https://api.untitles.net';

const TEST_USER = {
  loginId: 'admin',
  password: 'tjdals45!',
};

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // warm-up
    { duration: '1m',  target: 50 },   // ramp-up
    { duration: '2m',  target: 50 },   // steady-state
    { duration: '30s', target: 0  },   // cool-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500'],
    http_req_failed: ['rate<0.05'],
  },
};

const PARAMS = { headers: { 'Content-Type': 'application/json' } };

export default function () {

  group('01_로그인', () => {
    const res = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify(TEST_USER),
      PARAMS
    );
    check(res, { '로그인 200': (r) => r.status === 200 });
  });
  sleep(1);

  group('02_내정보_조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/auth/me`, PARAMS);
    check(res, { '내정보 200': (r) => r.status === 200 });
  });
  sleep(1);

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
    group('04_폴더트리_조회', () => {
      const res = http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}/folders`, PARAMS);
      check(res, { '폴더트리 200': (r) => r.status === 200 });
    });
    sleep(1);

    // CRUD — 게시글 생성 → 조회 → 삭제
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

    if (postId) {
      group('06_게시글_조회', () => {
        const res = http.get(
          `${BASE_URL}/api/v1/workspaces/${workspaceId}/posts/${postId}`,
          PARAMS
        );
        check(res, { '게시글조회 200': (r) => r.status === 200 });
      });
      sleep(1);

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

  group('08_로그아웃', () => {
    const res = http.post(`${BASE_URL}/api/v1/auth/logout`, null, PARAMS);
    check(res, { '로그아웃 200': (r) => r.status === 200 });
  });
  sleep(1);
}
