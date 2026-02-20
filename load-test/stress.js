import { loginAndSeed, cleanupSeed, writerScenario, readerScenario } from './common.js';

// ============================================================
//  Stress Test — Writer 55 + Reader 25 = VU 80, 6분
//  피크 시간대 (평균의 2~3배)
// ============================================================

export const options = {
  scenarios: {
    writers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m',  target: 55 },
        { duration: '3m',  target: 55 },
        { duration: '30s', target: 0  },
      ],
      exec: 'writer',
    },
    readers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m',  target: 25 },
        { duration: '3m',  target: 25 },
        { duration: '30s', target: 0  },
      ],
      exec: 'reader',
    },
  },
  thresholds: {
    http_req_duration:    ['p(95)<2000'],
    http_req_failed:      ['rate<0.05'],
    post_update_duration: ['p(95)<1000'],
    post_get_duration:    ['p(95)<500'],
    folder_tree_duration: ['p(95)<500'],
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
