import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://172.29.96.1:8070';
const SESSION_ID = __ENV.SESSION_ID;
const WORKSPACE_ID = __ENV.WORKSPACE_ID;
const POST_ID = __ENV.POST_ID;

export const options = {
    vus: 50,
    iterations: 50,
};

export default function () {
    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
        cookies: {
            JSESSIONID: SESSION_ID,
        },
    };

    const getRes = http.get(`${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/posts/${POST_ID}`, params);
    const post = JSON.parse(getRes.body);

    const payload = JSON.stringify({
        title: `수정 by VU ${__VU}`,
        content: `동시 수정 테스트 ${Date.now()}`,
        version: post.data.version,
    });

    const putRes = http.put(`${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/posts/${POST_ID}`, payload, params);

    check(putRes, {
        '200 또는 409': (r) => r.status === 200 || r.status === 409,
    });

    console.log(`VU ${__VU}: status=${putRes.status}`);
}