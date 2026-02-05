import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ─── 환경변수 ───
const BASE_URL     = __ENV.BASE_URL     || 'http://localhost:8070';
const SESSION_ID   = __ENV.SESSION_ID;
const WORKSPACE_ID = __ENV.WORKSPACE_ID || '1';

// 게시글 ID 목록 (랜덤 선택용)
const POST_IDS = [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14];

function randomPostId() {
    return POST_IDS[Math.floor(Math.random() * POST_IDS.length)];
}

// ─── 커스텀 메트릭 ───
const treeDuration   = new Trend('tree_duration',   true);
const detailDuration = new Trend('detail_duration', true);
const editDuration   = new Trend('edit_duration',   true);
const editConflict   = new Rate('edit_conflict');
const errorCount     = new Counter('error_count');

// ─── 읽기 50% / 쓰기 50% 시나리오 ───
export const options = {
    scenarios: {
        readers: {
            executor: 'ramping-vus',
            exec: 'readScenario',
            stages: [
                { duration: '30s',  target: 50  },
                { duration: '1m',   target: 150 },
                { duration: '2m',   target: 150 },
                { duration: '1m',   target: 300 },
                { duration: '2m',   target: 300 },
                { duration: '30s',  target: 0   },
            ],
        },
        writers: {
            executor: 'ramping-vus',
            exec: 'writeScenario',
            stages: [
                { duration: '30s',  target: 50  },
                { duration: '1m',   target: 150 },
                { duration: '2m',   target: 150 },
                { duration: '1m',   target: 300 },
                { duration: '2m',   target: 300 },
                { duration: '30s',  target: 0   },
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'],
        checks:            ['rate>0.6'],
    },
};

function getParams() {
    return {
        headers: { 'Content-Type': 'application/json' },
        cookies: { JSESSIONID: SESSION_ID },
    };
}

// ─── 읽기 시나리오: 트리 조회 → 게시글 상세 조회 → 이탈 ───
export function readScenario() {
    const params = getParams();
    const postId = randomPostId();

    // 1) 트리 조회 (폴더 목록)
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

    // 2) 게시글 상세 조회 (읽기만 하고 나감)
    const detailRes = http.get(
        `${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/posts/${postId}`,
        Object.assign({}, params, { tags: { name: 'GET /posts/{id}' } })
    );
    detailDuration.add(detailRes.timings.duration);

    check(detailRes, {
        'detail: status 200': (r) => r.status === 200,
    });

    if (detailRes.status !== 200) {
        errorCount.add(1);
    }

    // 읽고 나서 잠시 머무는 시간 (2~4초)
    sleep(2 + Math.random() * 2);
}

// ─── 쓰기 시나리오: 게시글 열고 → 타이핑하듯 PUT 반복 ───
export function writeScenario() {
    const params = getParams();
    const postId = randomPostId();

    // 1) 게시글 상세 조회 (편집 화면 진입)
    const detailRes = http.get(
        `${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/posts/${postId}`,
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

    // 2) 타이핑 시뮬레이션: 1초 디바운스 간격으로 PUT 3~6회 반복
    const typingCount = 3 + Math.floor(Math.random() * 4); // 3~6회

    for (let i = 0; i < typingCount; i++) {
        // 디바운스 대기 (1~1.5초 — 실제 타이핑 간격)
        sleep(1 + Math.random() * 0.5);

        const payload = JSON.stringify({
            title:   `Note VU${__VU} edit${i}`,
            content: `편집 중... ${new Date().toISOString()} (${i + 1}/${typingCount})`,
            version: version,
        });

        const editRes = http.put(
            `${BASE_URL}/api/v1/workspaces/${WORKSPACE_ID}/posts/${postId}`,
            payload,
            Object.assign({}, params, { tags: { name: 'PUT /posts/{id}' } })
        );
        editDuration.add(editRes.timings.duration);
        editConflict.add(editRes.status === 409);

        check(editRes, {
            'edit: 200 or 409': (r) => r.status === 200 || r.status === 409,
        });

        if (editRes.status === 200) {
            // 저장 성공 시 version 업데이트
            try {
                version = JSON.parse(editRes.body).data.version;
            } catch (e) { /* version 유지 */ }
        } else if (editRes.status === 409) {
            // 충돌 시 최신 version 가져와서 계속
            try {
                version = JSON.parse(editRes.body).data.version;
            } catch (e) { /* version 유지 */ }
        } else {
            errorCount.add(1);
            break; // 서버 에러면 타이핑 중단
        }
    }

    // 편집 끝나고 잠시 대기 (다음 노트로 이동 전)
    sleep(1 + Math.random());
}