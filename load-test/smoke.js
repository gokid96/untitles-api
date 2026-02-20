import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ============================================================
//  untitles-api k6 Smoke Test
//  VU 1~2명 — 시나리오 정상 동작 확인
// ============================================================

const BASE_URL = 'https://api.untitles.net';

const TEST_USER = {
  loginId: 'admin',
  password: 'tjdals45!',
};

export const options = {
  stages: [
    { duration: '30s', target: 2 },
    { duration: '30s', target: 2 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate==0'],
    checks: ['rate==1.0'],
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
    check(res, {
      '로그인 200': (r) => r.status === 200,
    });
  });
  sleep(1);

  group('02_내정보_조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/auth/me`, PARAMS);
    const body = res.json();
    check(res, {
      '내정보 200': (r) => r.status === 200,
      '인증됨': () => body.data.authenticated === true,
    });
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
    group('04_워크스페이스_상세', () => {
      const res = http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}`, PARAMS);
      check(res, { '워크스페이스상세 200': (r) => r.status === 200 });
    });
    sleep(0.5);

    group('05_폴더트리_조회', () => {
      const res = http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}/folders`, PARAMS);
      check(res, { '폴더트리 200': (r) => r.status === 200 });
    });
    sleep(1);
  }

  group('06_로그아웃', () => {
    const res = http.post(`${BASE_URL}/api/v1/auth/logout`, null, PARAMS);
    check(res, { '로그아웃 200': (r) => r.status === 200 });
  });
  sleep(1);
}
