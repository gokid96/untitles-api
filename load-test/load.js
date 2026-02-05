import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ─── 환경변수 ───
const BASE_URL     = __ENV.BASE_URL     || 'http://172.29.96.1:8070';
const SESSION_ID   = __ENV.SESSION_ID;
const WORKSPACE_ID = __ENV.WORKSPACE_ID || '1';
const POST_ID      = __ENV.POST_ID      || '3';

// ─── 커스텀 메트릭 ───
const treeDuration   = new Trend('tree_duration',   true);  // 트리 조회 응답시간
const detailDuration = new Trend('detail_duration', true);  // 상세 조회 응답시간
const editDuration   = new Trend('edit_duration',   true);  // 수정 응답시간
const editConflict   = new Rate('edit_conflict');            // 409 비율
const errorCount     = new Counter('error_count');           // 예상 외 에러

// ─── Load 시나리오: 10 → 50 → 50 유지 → 0 ───
export const options = {
    stages: [
        { duration: '30s', target: 10 },   // warm-up
        { duration: '30s', target: 50 },   // ramp-up
        { duration: '2m',  target: 50 },   // steady-state (핵심 구간)
        { duration: '30s', target: 0  },   // cool-down
    ],
    thresholds: {
        http_req_duration: ['p(95)<3000'],  // 95% 요청이 3초 이내
        checks:            ['rate>0.8'],    // check 통과율 80% 이상
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
        console.log(`VU ${__VU}: tree failed status=${treeRes.status}`);
        sleep(1);
        return;
    }

    sleep(0.5 + Math.random() * 0.5);  // 사용자가 트리를 보는 시간 (0.5~1초)

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
        console.log(`VU ${__VU}: detail failed status=${detailRes.status}`);
        sleep(1);
        return;
    }

    // version 추출
    let version = 0;
    try {
        version = JSON.parse(detailRes.body).data.version;
    } catch (e) {
        errorCount.add(1);
        console.log(`VU ${__VU}: version 파싱 실패`);
        sleep(1);
        return;
    }

    sleep(1 + Math.random());  // 사용자가 글을 읽는 시간 (1~2초)

    // ── 3) 수정 ──
    const payload = JSON.stringify({
        title:   `Load VU${__VU} iter${__ITER}`,
        content: `부하 테스트 — ${new Date().toISOString()}`,
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
        console.log(`VU ${__VU}: edit unexpected status=${editRes.status}`);
    }

    // ── 4) think time ──
    sleep(1 + Math.random());  // 다음 행동까지 대기 (1~2초)
}
