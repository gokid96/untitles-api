import http from 'k6/http';
import { check, sleep } from 'k6';

// ─── 환경변수 ───
const BASE_URL     = __ENV.BASE_URL;
const SESSION_ID   = __ENV.SESSION_ID;
const WORKSPACE_ID = __ENV.WORKSPACE_ID || '1';
const POST_ID      = __ENV.POST_ID      || '3';

// ─── Smoke: VU 1명, 1회 실행 ───
export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        checks: ['rate==1.0'],  // 모든 check 통과해야 성공
    },
};

function getParams() {
    return {
        headers: { 'Content-Type': 'application/json' },
        cookies: { JSESSIONID: SESSION_ID },
    };
}

export default function () {
    const params = getParams();

    // ── Step 1: 트리(폴더) 조회 ──
    console.log('── Step 1: 트리 조회');
    const treeRes = http.get(
        `${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/folders`,
        params
    );
    check(treeRes, {
        'tree: status 200': (r) => r.status === 200,
        'tree: body not empty': (r) => r.body && r.body.length > 0,
    });
    console.log(`   status=${treeRes.status}  duration=${treeRes.timings.duration}ms`);
    sleep(0.5);

    // ── Step 2: 게시글 상세 조회 ──
    console.log('── Step 2: 상세 조회');
    const detailRes = http.get(
        `${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/posts/${POST_ID}`,
        params
    );

    let version = 0;
    check(detailRes, {
        'detail: status 200': (r) => r.status === 200,
        'detail: has version': (r) => {
            try {
                version = JSON.parse(r.body).data.version;
                return version !== undefined;
            } catch { return false; }
        },
    });
    console.log(`   status=${detailRes.status}  duration=${detailRes.timings.duration}ms  version=${version}`);
    sleep(0.5);

    // ── Step 3: 게시글 수정 ──
    console.log('── Step 3: 수정');
    const payload = JSON.stringify({
        title: `Smoke 테스트 ${new Date().toISOString()}`,
        content: 'Smoke 테스트 — 기본 흐름 확인',
        version: version,
    });
    const editRes = http.put(
        `${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/posts/${POST_ID}`,
        payload,
        params
    );
    check(editRes, {
        'edit: status 200': (r) => r.status === 200,
    });
    console.log(`   status=${editRes.status}  duration=${editRes.timings.duration}ms`);

    console.log('');
    console.log('✅ Smoke 완료 — 모든 check 통과하면 Load 테스트 진행 가능');
}
