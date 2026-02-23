import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, HEADERS, TEST_USER } from './config.js';

/**
 * ============================================
 * 4단계: Spike Test (스파이크 테스트)
 * ============================================
 * 목적: 갑작스러운 트래픽 급증 대응 능력 확인
 * 실행: k6 run k6/04-spike.js -e BASE_URL=https://your-api.com
 * 
 * 시나리오: 평소 10명 → 갑자기 100명 → 다시 10명
 * 확인 포인트:
 *   - 급증 시 에러율
 *   - 급증 후 회복 시간
 *   - EC2 Auto Scaling 반응 속도 (설정되어 있다면)
 */

export const options = {
  stages: [
    { duration: '1m', target: 10 },    // 평상시 트래픽
    { duration: '30s', target: 10 },   // 유지
    { duration: '10s', target: 100 },  // 갑자기 100명으로 급증!
    { duration: '2m', target: 100 },   // 100명 유지 (서버 반응 관찰)
    { duration: '10s', target: 10 },   // 급감
    { duration: '2m', target: 10 },    // 회복 확인
    { duration: '30s', target: 0 },    // 종료
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000'],  // 스파이크 시 3초까지 허용
    http_req_failed: ['rate<0.15'],     // 15%까지 허용 (급증이니까)
  },
};

export default function () {
  const jar = http.cookieJar();

  // 로그인
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      loginId: TEST_USER.loginId,
      password: TEST_USER.password,
    }),
    { headers: HEADERS, jar: jar }
  );

  if (loginRes.status !== 200) {
    sleep(0.5);
    return;
  }

  // 주요 읽기 API들 동시 호출
  http.get(`${BASE_URL}/api/v1/auth/me`, { headers: HEADERS, jar: jar });

  const wsRes = http.get(`${BASE_URL}/api/v1/workspaces`, {
    headers: HEADERS,
    jar: jar,
  });

  let workspaceId;
  if (wsRes.status === 200) {
    const body = JSON.parse(wsRes.body);
    const list = body.data || body;
    if (Array.isArray(list) && list.length > 0) {
      workspaceId = list[0].workspaceId;
    }
  }

  if (workspaceId) {
    http.get(`${BASE_URL}/api/v1/workspaces/${workspaceId}/folders`, {
      headers: HEADERS,
      jar: jar,
    });
  }

  sleep(0.5);
}
