import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ============================================================
//  untitles-api k6 Spike Test
//  VU 10→200 급등→10 — 갑자기 트래픽이 몰리는 상황 시뮬레이션
// ============================================================

const BASE_URL = 'https://api.untitles.net';

const TEST_USER = {
  loginId: 'admin',
  password: 'tjdals45!',
};

export const options = {
  stages: [
    { duration: '30s', target: 10  },   // 평소 트래픽
    { duration: '10s', target: 200 },   // 급등! (10초 만에 200명)
    { duration: '1m',  target: 200 },   // 폭주 유지
    { duration: '10s', target: 10  },   // 급감
    { duration: '30s', target: 10  },   // 회복 관찰
    { duration: '20s', target: 0   },   // cool-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.20'],
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
  sleep(0.5);

  group('02_내정보', () => {
    const res = http.get(`${BASE_URL}/api/v1/auth/me`, PARAMS);
    check(res, { '내정보 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  let workspaceId;
  group('03_워크스페이스목록', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces`, PARAMS);
    check(res, { '워크스페이스 200': (r) => r.status === 200 });
    const body = res.json();
    const list = body.data || body;
    if (Array.isArray(list) && list.length > 0) {
      workspaceId = list[0].workspaceId || list[0].id;
    }
  });
  sleep(0.5);

  if (workspaceId) {
    group('04_폴더트리', () => {
      const res = http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}/folders`, PARAMS);
      check(res, { '폴더트리 200': (r) => r.status === 200 });
    });
    sleep(0.5);
  }

  group('05_로그아웃', () => {
    const res = http.post(`${BASE_URL}/api/v1/auth/logout`, null, PARAMS);
    check(res, { '로그아웃 200': (r) => r.status === 200 });
  });
  sleep(0.5);
}
