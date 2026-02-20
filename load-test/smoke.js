import { loginAndSeed, cleanupSeed, writerScenario } from './common.js';

// ============================================================
//  Smoke Test — VU 5, 1분
//  서버 정상 동작 확인
// ============================================================

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed:   ['rate<0.01'],
  },
};

export function setup() {
  return loginAndSeed(3, 15);
}

export default function (data) {
  writerScenario(data);
}

export function teardown(data) {
  cleanupSeed(data);
}
