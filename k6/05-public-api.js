import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, HEADERS } from './config.js';

/**
 * ============================================
 * 공개 API 전용 부하 테스트
 * ============================================
 * 목적: 인증 불필요한 공개 페이지 성능 확인
 * 실행: k6 run k6/05-public-api.js -e BASE_URL=https://your-api.com -e SLUG=your-slug
 * 
 * 공개 블로그/포트폴리오 페이지는 불특정 다수가 접근하므로
 * 별도로 테스트하는 것이 좋음
 */

const SLUG = __ENV.SLUG || 'test-workspace';

export const options = {
  stages: [
    { duration: '1m', target: 20 },
    { duration: '3m', target: 50 },
    { duration: '3m', target: 100 },  // 공개 페이지는 인증 없이 더 많은 유저 가능
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'],  // 공개 읽기 API는 빨라야 함
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  // 공개 워크스페이스 조회
  const wsRes = http.get(`${BASE_URL}/api/v1/public/${SLUG}`);
  check(wsRes, {
    '공개 워크스페이스 200': (r) => r.status === 200,
  });

  // 공개 게시글이 있다면 조회
  if (wsRes.status === 200) {
    const body = JSON.parse(wsRes.body);
    const posts = body.posts || (body.data && body.data.posts);

    if (posts && posts.length > 0) {
      // 랜덤 게시글 하나 선택해서 상세 조회
      const randomPost = posts[Math.floor(Math.random() * posts.length)];
      const postRes = http.get(
        `${BASE_URL}/api/v1/public/${SLUG}/posts/${randomPost.postId}`
      );
      check(postRes, {
        '공개 게시글 200': (r) => r.status === 200,
      });
    }
  }

  // 헬스체크
  http.get(`${BASE_URL}/actuator/health`);

  sleep(0.5);
}
