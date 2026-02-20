import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ============================================================
//  untitles-api k6 Stress Test (세션 유지)
//  setup에서 로그인 1회 → 세션 쿠키로 API만 반복
//  BCrypt 병목 제거 → 순수 서버 처리 성능 측정
// ============================================================

const BASE_URL = 'https://api.untitles.net';

const TEST_USER = {
  loginId: 'admin',
  password: 'tjdals45!',
};

export const options = {
  stages: [
    { duration: '30s', target: 50  },
    { duration: '1m',  target: 100 },
    { duration: '2m',  target: 100 },
    { duration: '1m',  target: 200 },
    { duration: '2m',  target: 200 },
    { duration: '30s', target: 0   },
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.20'],
  },
};

const PARAMS = { headers: { 'Content-Type': 'application/json' } };

// ── setup: 로그인 1회, 세션 쿠키 추출 ──
export function setup() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify(TEST_USER),
    PARAMS
  );
  check(res, { 'setup 로그인 200': (r) => r.status === 200 });

  // Set-Cookie 헤더에서 세션 쿠키 추출
  const setCookie = res.headers['Set-Cookie'] || '';
  const match = setCookie.match(/(?:SESSION|JSESSIONID)=([^;]+)/);
  const sessionCookie = match ? match[0] : '';

  console.log(`세션 쿠키: ${sessionCookie}`);
  return { sessionCookie };
}

export default function (data) {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Cookie': data.sessionCookie,
    },
  };

  // 1. 내 정보 조회
  group('01_내정보', () => {
    const res = http.get(`${BASE_URL}/api/v1/auth/me`, params);
    check(res, { '내정보 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 2. 워크스페이스 목록
  let workspaceId;
  group('02_워크스페이스목록', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces`, params);
    check(res, { '워크스페이스 200': (r) => r.status === 200 });
    try {
      const body = res.json();
      const list = body.data || body;
      if (Array.isArray(list) && list.length > 0) {
        workspaceId = list[0].workspaceId || list[0].id;
      }
    } catch (e) {}
  });
  sleep(0.5);

  // 3. 폴더 트리 조회
  if (workspaceId) {
    group('03_폴더트리', () => {
      const res = http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}/folders`, params);
      check(res, { '폴더트리 200': (r) => r.status === 200 });
    });
    sleep(0.5);
  }

  sleep(0.5);
}
