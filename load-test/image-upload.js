import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8070';
const SESSION_ID = __ENV.SESSION_ID;

export const options = {
    stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        'http_req_duration': ['p(95)<500'],
    },
};

export default function () {
    const res = http.get(`${BASE_URL}/api/posts`, {
        cookies: { JSESSIONID: SESSION_ID },
    });

    check(res, {
        'status 200': (r) => r.status === 200,
    });

    sleep(1);
}