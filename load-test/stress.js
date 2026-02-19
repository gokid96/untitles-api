import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ============================================================
//  untitles-api k6 Stress Test
//  서버 한계점 찾기
// ============================================================

const BASE_URL = 'https://api.untitles.net';

const TEST_USER = {
  loginId: 'admin',
  password: 'tjdals45!',
};

export const options = {
  stages: [
    { duration: '30s',  target: 50  },   // Load에서 안정적이었던 구간
    { duration: '1m',   target: 100 },   // 2배로 증가
    { duration: '2m',   target: 100 },   // 100명 유지 — 여기서 버티는지 관찰
    { duration: '1m',   target: 200 },   // 한계 탐색
    { duration: '2m',   target: 200 },   // 200명 유지 — 터지는 지점 확인
    { duration: '30s',  target: 0   },   // cool-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],  // 5초까지 허용 (한계 탐색)
    http_req_failed: ['rate<0.20'],     // 20% 이내
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
