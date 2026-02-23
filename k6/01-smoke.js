import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, HEADERS, TEST_USER, DEFAULT_THRESHOLDS } from './config.js';

/**
 * ============================================
 * 1단계: Smoke Test (스모크 테스트)
 * ============================================
 * 목적: 서버가 정상 동작하는지 최소한의 부하로 확인
 * 실행: k6 run k6/01-smoke.js -e BASE_URL=https://your-api.com
 */

export const options = {
  vus: 1,              // 가상 유저 1명
  duration: '30s',     // 30초간 실행
  thresholds: {
    http_req_duration: ['p(95)<1000'],  // 스모크는 좀 널널하게
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  // 1. 헬스체크
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  check(healthRes, {
    'health check 200': (r) => r.status === 200,
    'health UP': (r) => JSON.parse(r.body).status === 'UP',
  });

  // 2. 로그인
  const jar = http.cookieJar();
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      loginId: TEST_USER.loginId,
      password: TEST_USER.password,
    }),
    { headers: HEADERS, jar: jar }
  );
  check(loginRes, {
    '로그인 성공': (r) => r.status === 200,
  });

  // 3. 내 정보 확인
  const meRes = http.get(`${BASE_URL}/api/v1/auth/me`, {
    headers: HEADERS,
    jar: jar,
  });
  check(meRes, {
    '인증 확인': (r) => r.status === 200,
    '인증됨': (r) => JSON.parse(r.body).data.authenticated === true,
  });

  // 4. 워크스페이스 목록 조회
  const wsRes = http.get(`${BASE_URL}/api/v1/workspaces`, {
    headers: HEADERS,
    jar: jar,
  });
  check(wsRes, {
    '워크스페이스 조회': (r) => r.status === 200,
  });

  // 5. 공개 페이지 (인증 불필요)
  // slug가 있다면 테스트, 없으면 스킵
  // const publicRes = http.get(`${BASE_URL}/api/v1/public/your-slug`);
  // check(publicRes, { '공개 페이지': (r) => r.status === 200 });

  sleep(1);
}
