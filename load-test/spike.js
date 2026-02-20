import { loginAndSeed, cleanupSeed, writerScenario, readerScenario } from './common.js';

// ============================================================
//  Spike Test — VU 150까지 급등, 6분
//  예상치 못한 트래픽 급증 (한계 테스트)
// ============================================================

export const options = {
  scenarios: {
    writers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10  },  // 워밍업
        { duration: '30s', target: 100 },  // 급등
        { duration: '3m',  target: 100 },  // 유지
        { duration: '30s', target: 10  },  // 회복
        { duration: '1m',  target: 0   },
      ],
      exec: 'writer',
    },
    readers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5  },
        { duration: '30s', target: 50 },
        { duration: '3m',  target: 50 },
        { duration: '30s', target: 5  },
        { duration: '1m',  target: 0  },
      ],
      exec: 'reader',
    },
  },
  thresholds: {
    http_req_duration:    ['p(95)<5000'],
    http_req_failed:      ['rate<0.10'],
    post_update_duration: ['p(95)<3000'],
    post_get_duration:    ['p(95)<2000'],
    folder_tree_duration: ['p(95)<2000'],
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
