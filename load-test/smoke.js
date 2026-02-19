import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ============================================================
//  untitles-api k6 Smoke Test
//  세션 기반 인증 → 주요 API 흐름 테스트
// ============================================================

const BASE_URL = 'https://api.untitles.net';

const TEST_USER = {
  loginId: 'admin',
  password: 'tjdals45!',
};

export const options = {
  stages: [
    { duration: '30s', target: 3 },
    { duration: '1m', target: 3 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
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
    check(res, {
      '로그인 200': (r) => r.status === 200,
    });
  });
  sleep(1);

  // 2. 내 정보 확인
  group('02_내정보_조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/auth/me`, PARAMS);
    const body = res.json();
    check(res, {
      '내정보 200': (r) => r.status === 200,
      '인증됨': () => body.data.authenticated === true,
    });
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
    // 4. 워크스페이스 상세
    group('04_워크스페이스_상세', () => {
      const res = http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}`, PARAMS);
      check(res, { '워크스페이스상세 200': (r) => r.status === 200 });
    });
    sleep(0.5);

    // 5. 폴더 트리 조회
    group('05_폴더트리_조회', () => {
      const res = http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}/folders`, PARAMS);
      check(res, { '폴더트리 200': (r) => r.status === 200 });
    });
    sleep(1);
  }

  // 6. 로그아웃
  group('06_로그아웃', () => {
    const res = http.post(`${BASE_URL}/api/v1/auth/logout`, null, PARAMS);
    check(res, { '로그아웃 200': (r) => r.status === 200 });
  });
  sleep(1);
}
