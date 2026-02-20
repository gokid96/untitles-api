import { loginAndSeed, cleanupSeed, writerScenario, readerScenario } from './common.js';

// ============================================================
//  Load Test — Writer 20 + Reader 10 = VU 30, 5분
//  DAU 400명 일상 부하 (평균 동시접속 28명)
// ============================================================

export const options = {
  scenarios: {
    writers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '4m',  target: 20 },
        { duration: '30s', target: 0  },
      ],
      exec: 'writer',
    },
    readers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '4m',  target: 10 },
        { duration: '30s', target: 0  },
      ],
      exec: 'reader',
    },
  },
  thresholds: {
    http_req_duration:    ['p(95)<1000'],
    http_req_failed:      ['rate<0.05'],
    post_update_duration: ['p(95)<500'],
    post_get_duration:    ['p(95)<300'],
    folder_tree_duration: ['p(95)<300'],
  },
};

export function setup() {
  return loginAndSeed(5, 30);
}

export function writer(data) {
  writerScenario(data);
}

export function reader(data) {
  readerScenario(data);
}

export function teardown(data) {
  cleanupSeed(data);
}
