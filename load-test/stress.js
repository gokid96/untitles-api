import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ─── 환경변수 ───
const BASE_URL     = __ENV.BASE_URL     || 'http://localhost:8070';
const SESSION_ID   = __ENV.SESSION_ID;
const WORKSPACE_ID = __ENV.WORKSPACE_ID || '1';
const POST_ID      = __ENV.POST_ID      || '3';

// ─── 커스텀 메트릭 ───
const treeDuration   = new Trend('tree_duration',   true);
const detailDuration = new Trend('detail_duration', true);
const editDuration   = new Trend('edit_duration',   true);
const editConflict   = new Rate('edit_conflict');
const errorCount     = new Counter('error_count');

// ─── Stress 시나리오: 50 → 100 → 200 → 0 ───
export const options = {
    stages: [
        { duration: '30s',  target: 50  },   // Load에서 안정적이었던 구간
        { duration: '1m',   target: 100 },   // 2배로 증가
        { duration: '2m',   target: 100 },   // 100명 유지 — 여기서 버티는지 관찰
        { duration: '1m',   target: 200 },   // 한계 탐색
        { duration: '2m',   target: 200 },   // 200명 유지 — 터지는 지점 확인
        { duration: '30s',  target: 0   },   // cool-down
    ],
    thresholds: {
        http_req_duration: ['p(95)<5000'],  // 5초까지 허용 (한계 탐색이니까 느슨하게)
        checks:            ['rate>0.6'],    // 60% 이상 통과
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

    // ── 1) 트리 조회 ──
    const treeRes = http.get(
        `${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/folders`,
        Object.assign({}, params, { tags: { name: 'GET /folders' } })
    );
    treeDuration.add(treeRes.timings.duration);

    const treeOk = check(treeRes, {
        'tree: status 200': (r) => r.status === 200,
    });

    if (!treeOk) {
        errorCount.add(1);
        sleep(1);
        return;
    }

    sleep(0.5 + Math.random() * 0.5);

    // ── 2) 상세 조회 ──
    const detailRes = http.get(
        `${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/posts/${POST_ID}`,
        Object.assign({}, params, { tags: { name: 'GET /posts/{id}' } })
    );
    detailDuration.add(detailRes.timings.duration);

    const detailOk = check(detailRes, {
        'detail: status 200': (r) => r.status === 200,
    });

    if (!detailOk) {
        errorCount.add(1);
        sleep(1);
        return;
    }

    let version = 0;
    try {
        version = JSON.parse(detailRes.body).data.version;
    } catch (e) {
        errorCount.add(1);
        sleep(1);
        return;
    }

    sleep(1 + Math.random());

    // ── 3) 수정 ──
    const payload = JSON.stringify({
        title:   `Stress VU${__VU} iter${__ITER}`,
        content: `스트레스 테스트 — ${new Date().toISOString()}`,
        version: version,
    });

    const editRes = http.put(
        `${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/posts/${POST_ID}`,
        payload,
        Object.assign({}, params, { tags: { name: 'PUT /posts/{id}' } })
    );
    editDuration.add(editRes.timings.duration);
    editConflict.add(editRes.status === 409);

    check(editRes, {
        'edit: 200 or 409': (r) => r.status === 200 || r.status === 409,
    });

    if (editRes.status !== 200 && editRes.status !== 409) {
        errorCount.add(1);
    }

    sleep(1 + Math.random());
}
