import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ============================================================
//  untitles-api k6 실사용 패턴 시나리오 (v2 - seed data 방식)
//
//  제약 조건:
//    - 폴더 최대 20개
//    - 게시글(노트) 최대 50개
//
//  전략:
//    setup()에서 시드 데이터(폴더 5개 + 노트 30개) 생성
//    시나리오에서는 기존 데이터 수정/조회 위주 (실제 사용 패턴)
//    teardown()에서 시드 데이터 전부 삭제
//
//  트래픽 비율 (실제 노트앱 패턴):
//    읽기(GET) 60% / 수정(PUT) 30% / 생성+삭제 10%
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'https://api.untitles.net';
const WORKSPACE_ID = __ENV.WORKSPACE_ID || '6';

const TEST_USER = {
  loginId: __ENV.LOGIN_ID || 'admin',
  password: __ENV.PASSWORD || 'tjdals45!',
};

// ── 시드 데이터 설정 ──
const SEED_FOLDERS = 5;   // setup에서 생성할 폴더 수 (제한 20개 중 5개만 사용)
const SEED_POSTS   = 30;  // setup에서 생성할 노트 수 (제한 50개 중 30개만 사용)

// ── 커스텀 메트릭 ──
const postCreateDuration = new Trend('post_create_duration', true);
const postUpdateDuration = new Trend('post_update_duration', true);
const postGetDuration    = new Trend('post_get_duration', true);
const folderTreeDuration = new Trend('folder_tree_duration', true);
const errorCount         = new Counter('business_errors');
const optimisticLockCount = new Counter('optimistic_lock_conflicts');

export const options = {
  scenarios: {
    // 시나리오 1: 노트 작성자 (수정 위주)
    note_writers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 30  },
        { duration: '2m',  target: 50  },
        { duration: '1m',  target: 100 },
        { duration: '2m',  target: 100 },
        { duration: '30s', target: 0   },
      ],
      exec: 'noteWriterScenario',
    },
    // 시나리오 2: 읽기 위주 사용자
    readers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20  },
        { duration: '2m',  target: 30  },
        { duration: '1m',  target: 50  },
        { duration: '2m',  target: 50  },
        { duration: '30s', target: 0   },
      ],
      exec: 'readerScenario',
    },
  },
  thresholds: {
    http_req_duration:     ['p(95)<3000'],
    http_req_failed:       ['rate<0.05'],
    post_create_duration:  ['p(95)<2000'],
    post_update_duration:  ['p(95)<2000'],
    post_get_duration:     ['p(95)<1000'],
    folder_tree_duration:  ['p(95)<1500'],
    optimistic_lock_conflicts: ['count<500'],  // 낙관적 락 충돌 모니터링 (임계치 초과 시 경고)
  },
};

const JSON_PARAMS = { headers: { 'Content-Type': 'application/json' } };

// ── 헬퍼: 더미 노트 컨텐츠 ──
function generateNoteContent(sizeKB) {
  const paragraph = '<p>이것은 테스트용 노트 내용입니다. 실제 사용자가 작성하는 것처럼 적당한 분량의 텍스트를 포함합니다. </p>';
  const repeat = Math.ceil((sizeKB * 1024) / paragraph.length);
  return paragraph.repeat(repeat).substring(0, sizeKB * 1024);
}

const SMALL_CONTENT  = generateNoteContent(1);
const MEDIUM_CONTENT = generateNoteContent(5);
const LARGE_CONTENT  = generateNoteContent(20);

// ── 헬퍼: 배열에서 랜덤 선택 ──
function randomPick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// ============================================================
//  setup: 로그인 + 시드 데이터 생성
// ============================================================
export function setup() {
  // 1. 로그인
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify(TEST_USER),
    JSON_PARAMS
  );
  check(loginRes, { 'setup 로그인 200': (r) => r.status === 200 });

  const setCookie = loginRes.headers['Set-Cookie'] || '';
  const match = setCookie.match(/(?:SESSION|JSESSIONID)=([^;]+)/);
  const sessionCookie = match ? match[0] : '';

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Cookie': sessionCookie,
    },
  };

  const wsId = WORKSPACE_ID;
  console.log(`세션: ${sessionCookie}, 워크스페이스: ${wsId}`);

  // 2. 시드 폴더 생성
  const folderIds = [];
  for (let i = 0; i < SEED_FOLDERS; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/workspaces/${wsId}/folders`,
      JSON.stringify({
        name: `k6-seed-folder-${i}-${Date.now()}`,
        parentId: null,
        orderIndex: i,
      }),
      params
    );
    if (res.status === 201 || res.status === 200) {
      try {
        const body = res.json();
        const id = (body.data || body).folderId || (body.data || body).id;
        if (id) folderIds.push(id);
      } catch (e) {}
    }
    sleep(0.1);
  }
  console.log(`시드 폴더 ${folderIds.length}개 생성 완료`);

  // 3. 시드 노트 생성 (폴더에 분산 배치)
  const postIds = [];
  for (let i = 0; i < SEED_POSTS; i++) {
    const folderId = folderIds.length > 0 ? folderIds[i % folderIds.length] : null;
    const res = http.post(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts`,
      JSON.stringify({
        title: `k6-seed-note-${i}`,
        content: SMALL_CONTENT,
        folderId: folderId,
      }),
      params
    );
    if (res.status === 200 || res.status === 201) {
      try {
        const body = res.json();
        const id = (body.data || body).postId || (body.data || body).id;
        if (id) postIds.push(id);
      } catch (e) {}
    }
    sleep(0.1);
  }
  console.log(`시드 노트 ${postIds.length}개 생성 완료`);

  return { sessionCookie, workspaceId: wsId, folderIds, postIds };
}

// ── 공통: 인증 헤더 ──
function authParams(data) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'Cookie': data.sessionCookie,
    },
  };
}

// ============================================================
//  시나리오 1: 노트 작성자 (Writer)
//  기존 노트를 랜덤 선택 → 수정(자동저장) → 조회
//  가끔 생성+즉시삭제로 슬롯 순환 (10% 확률)
// ============================================================
export function noteWriterScenario(data) {
  if (!data.postIds || data.postIds.length === 0) return;

  const params = authParams(data);
  const wsId = data.workspaceId;
  const vuId = __VU;
  const iterNum = __ITER;

  // 시드 노트 중 랜덤 선택
  const targetPostId = randomPick(data.postIds);

  // 1. 트리 조회 (페이지 진입)
  group('writer_01_트리조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
    folderTreeDuration.add(res.timings.duration);
    check(res, { '트리조회 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 2. 노트 열기 (GET)
  group('writer_02_노트열기', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
      params
    );
    postGetDuration.add(res.timings.duration);
    check(res, { '노트열기 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 3. 자동저장 시뮬레이션 (PUT 3~5회)
  const autoSaveCount = 3 + Math.floor(Math.random() * 3);
  for (let i = 0; i < autoSaveCount; i++) {
    group('writer_03_자동저장', () => {
      const content = i < 2 ? SMALL_CONTENT : (i < 4 ? MEDIUM_CONTENT : LARGE_CONTENT);
      const payload = JSON.stringify({
        title: `k6-note-edited-${vuId}-${iterNum}-save${i + 1}`,
        content: content,
      });
      const res = http.put(
        `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
        payload,
        params
      );
      postUpdateDuration.add(res.timings.duration);

      // 409(낙관적 락 충돌)는 비즈니스 정상 → 별도 메트릭으로 추적
      if (res.status === 409) {
        optimisticLockCount.add(1);
      }
      const ok = check(res, {
        '자동저장 성공': (r) => r.status === 200 || r.status === 409,
      });
      if (!ok) errorCount.add(1); // 409도 아닌 진짜 에러만 카운트
    });
    sleep(1 + Math.random()); // 1~2초 간격 (타이핑 시뮬레이션)
  }

  // 4. 저장 후 노트 재조회
  group('writer_04_노트재조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
      params
    );
    postGetDuration.add(res.timings.duration);
    check(res, { '노트재조회 200': (r) => r.status === 200 });
  });
  sleep(0.3);

  // 5. 트리 재조회 (사이드바 갱신)
  group('writer_05_트리재조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
    folderTreeDuration.add(res.timings.duration);
    check(res, { '트리재조회 200': (r) => r.status === 200 });
  });

  // 6. 가끔 생성 → 즉시 삭제 (슬롯 순환 테스트, 10% 확률)
  if (Math.random() < 0.1) {
    let tempPostId = null;
    group('writer_06_생성삭제_순환', () => {
      // 생성
      const createRes = http.post(
        `${BASE_URL}/api/v1/workspaces/${wsId}/posts`,
        JSON.stringify({
          title: `k6-temp-${vuId}-${Date.now()}`,
          content: SMALL_CONTENT,
          folderId: data.folderIds.length > 0 ? randomPick(data.folderIds) : null,
        }),
        params
      );
      postCreateDuration.add(createRes.timings.duration);
      const created = check(createRes, { '임시노트생성 200': (r) => r.status === 200 });

      if (created) {
        try {
          const body = createRes.json();
          tempPostId = (body.data || body).postId || (body.data || body).id;
        } catch (e) {}
      }

      // 즉시 삭제
      if (tempPostId) {
        sleep(0.3);
        const delRes = http.del(
          `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${tempPostId}`,
          null,
          params
        );
        check(delRes, { '임시노트삭제 204': (r) => r.status === 204 });
      }
    });
  }

  sleep(1);
}

// ============================================================
//  시나리오 2: 읽기 위주 사용자 (Reader)
// ============================================================
export function readerScenario(data) {
  if (!data.workspaceId) return;

  const params = authParams(data);
  const wsId = data.workspaceId;

  // 1. 내 정보 조회
  group('reader_01_내정보', () => {
    const res = http.get(`${BASE_URL}/api/v1/auth/me`, params);
    check(res, { '내정보 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 2. 워크스페이스 목록
  group('reader_02_워크스페이스목록', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces`, params);
    check(res, { '워크스페이스목록 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 3. 워크스페이스 상세
  group('reader_03_워크스페이스상세', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}`, params);
    check(res, { '워크스페이스상세 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 4. 폴더 트리 조회
  group('reader_04_트리조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/folders`, params);
    folderTreeDuration.add(res.timings.duration);
    check(res, { '트리조회 200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // 5. 시드 노트 중 랜덤 조회 (실제 사용: 노트 클릭해서 읽기)
  if (data.postIds && data.postIds.length > 0) {
    const targetPostId = randomPick(data.postIds);
    group('reader_05_노트조회', () => {
      const res = http.get(
        `${BASE_URL}/api/v1/workspaces/${wsId}/posts/${targetPostId}`,
        params
      );
      postGetDuration.add(res.timings.duration);
      check(res, { '노트조회 200': (r) => r.status === 200 });
    });
    sleep(0.5);
  }

  // 6. 멤버 목록 조회
  group('reader_06_멤버목록', () => {
    const res = http.get(`${BASE_URL}/api/v1/workspaces/${wsId}/members`, params);
    check(res, { '멤버목록 200': (r) => r.status === 200 });
  });

  sleep(1 + Math.random());
}

// ============================================================
//  teardown: 시드 데이터 정리
// ============================================================
export function teardown(data) {
  if (!data.sessionCookie) return;

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Cookie': data.sessionCookie,
    },
  };
  const wsId = data.workspaceId;

  // 노트 삭제
  if (data.postIds) {
    for (const postId of data.postIds) {
      http.del(`${BASE_URL}/api/v1/workspaces/${wsId}/posts/${postId}`, null, params);
      sleep(0.05);
    }
    console.log(`시드 노트 ${data.postIds.length}개 삭제 완료`);
  }

  // 폴더 삭제
  if (data.folderIds) {
    for (const folderId of data.folderIds) {
      http.del(`${BASE_URL}/api/v1/workspaces/${wsId}/folders/${folderId}`, null, params);
      sleep(0.05);
    }
    console.log(`시드 폴더 ${data.folderIds.length}개 삭제 완료`);
  }
}