import http from 'k6/http';
import { check, sleep } from 'k6';

// ============================================================
//  데이터 시딩 스크립트 (실제 운영 패턴)
//
//  사용자 200명 × 워크스페이스 1개 × 노트 50개 = 10,000건
//  제한 변경 없이 실제 운영과 동일한 구조
//
//  실행: k6 run seed-data.js
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'https://api.untitles.net';
const TOTAL_USERS = 200;
const POSTS_PER_USER = 50;
const FOLDERS_PER_USER = 5; // 폴더 5개에 노트 분산

export const options = {
  vus: 1,
  iterations: 1,
  duration: '120m',
};

const JSON_PARAMS = { headers: { 'Content-Type': 'application/json' } };

function generateContent(i) {
  const size = 500 + Math.floor(Math.random() * 2000);
  const base = `<p>노트 #${i}의 내용입니다. 이 텍스트는 부하 테스트를 위한 시드 데이터입니다. 실제 사용자가 작성하는 노트와 비슷한 분량을 시뮬레이션합니다. </p>`;
  return base.repeat(Math.ceil(size / base.length)).substring(0, size);
}

export default function () {
  let totalPosts = 0;
  let totalFolders = 0;
  let totalUsers = 0;

  for (let u = 0; u < TOTAL_USERS; u++) {
    const userNum = u + 1;
    const loginId = `seeduser${String(userNum).padStart(3, '0')}`;
    const email = `${loginId}@test.com`;
    const password = 'Test1234!';
    const nickname = `테스트유저${userNum}`;

    // 1. 회원가입
    const signupRes = http.post(
      `${BASE_URL}/api/v1/auth/signup`,
      JSON.stringify({ email, loginId, password, nickname }),
      JSON_PARAMS
    );

    if (signupRes.status !== 201 && signupRes.status !== 200) {
      if (userNum <= 3) {
        console.log(`회원가입 실패 #${userNum}: status=${signupRes.status}, body=${signupRes.body.substring(0, 200)}`);
      }
      // 이미 존재하면 로그인으로 진행
      const loginRes = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ loginId, password }),
        JSON_PARAMS
      );
      if (loginRes.status !== 200) {
        console.log(`로그인도 실패 #${userNum}: ${loginRes.status}`);
        continue;
      }
      var setCookie = loginRes.headers['Set-Cookie'] || '';
    } else {
      var setCookie = signupRes.headers['Set-Cookie'] || '';
    }

    const match = setCookie.match(/(?:SESSION|JSESSIONID)=([^;]+)/);
    const sessionCookie = match ? match[0] : '';
    if (!sessionCookie) {
      console.log(`세션 없음 #${userNum}`);
      continue;
    }

    const params = {
      headers: { 'Content-Type': 'application/json', 'Cookie': sessionCookie },
    };

    totalUsers++;

    // 2. 워크스페이스 생성
    const wsRes = http.post(
      `${BASE_URL}/api/v1/workspaces`,
      JSON.stringify({ name: `ws-${loginId}`, description: `${nickname}의 워크스페이스` }),
      params
    );

    let wsId = null;
    if (wsRes.status === 200 || wsRes.status === 201) {
      try {
        const body = wsRes.json();
        wsId = (body.data || body).workspaceId || (body.data || body).id;
      } catch (e) {}
    }

    if (!wsId) {
      // 이미 있으면 목록에서 가져오기
      const listRes = http.get(`${BASE_URL}/api/v1/workspaces`, params);
      try {
        const body = listRes.json();
        const list = body.data || body;
        if (Array.isArray(list) && list.length > 0) {
          wsId = list[0].workspaceId || list[0].id;
        }
      } catch (e) {}
    }

    if (!wsId) {
      console.log(`워크스페이스 없음 #${userNum}`);
      continue;
    }

    // 3. 폴더 생성
    const folderIds = [];
    for (let f = 0; f < FOLDERS_PER_USER; f++) {
      const folderRes = http.post(
        `${BASE_URL}/api/v1/workspaces/${wsId}/folders`,
        JSON.stringify({ name: `folder-${f + 1}`, parentId: null, orderIndex: f }),
        params
      );
      if (folderRes.status === 201 || folderRes.status === 200) {
        try {
          const body = folderRes.json();
          const id = (body.data || body).folderId || (body.data || body).id;
          if (id) folderIds.push(id);
        } catch (e) {}
      }
      sleep(0.02);
    }
    totalFolders += folderIds.length;

    // 4. 노트 생성 (50개)
    let userPosts = 0;
    for (let p = 0; p < POSTS_PER_USER; p++) {
      const folderId = folderIds.length > 0 ? folderIds[p % folderIds.length] : null;
      const postRes = http.post(
        `${BASE_URL}/api/v1/workspaces/${wsId}/posts`,
        JSON.stringify({
          title: `note-${loginId}-${p + 1}`,
          content: generateContent(p),
          folderId: folderId,
        }),
        params
      );
      if (postRes.status === 200 || postRes.status === 201) {
        userPosts++;
      }
      sleep(0.02);
    }
    totalPosts += userPosts;

    // 진행 로그 (10명마다)
    if (userNum % 10 === 0) {
      console.log(`진행: ${userNum}/${TOTAL_USERS}명 완료 (누적 노트: ${totalPosts}건)`);
    }

    sleep(0.1);
  }

  console.log(`\n========================================`);
  console.log(`시딩 완료!`);
  console.log(`사용자: ${totalUsers}명`);
  console.log(`폴더: ${totalFolders}개`);
  console.log(`노트: ${totalPosts}건`);
  console.log(`========================================`);
}
