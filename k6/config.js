/**
 * k6 테스트 공통 설정
 * 
 * 사용법:
 * 1. BASE_URL을 EC2 서버 주소로 변경
 * 2. TEST_USER를 실제 테스트 계정으로 변경
 */

// ============================================
// 🔧 여기만 수정하세요
// ============================================
export const BASE_URL = __ENV.BASE_URL || 'https://your-ec2-domain.com';

// 테스트용 계정 (미리 회원가입해두세요)
export const TEST_USER = {
  loginId: __ENV.TEST_LOGIN_ID || 'admin',
  password: __ENV.TEST_PASSWORD || 'tjdals45!',
};

// ============================================
// 공통 헤더
// ============================================
export const HEADERS = {
  'Content-Type': 'application/json',
};

// ============================================
// 공통 Thresholds (성능 기준)
// ============================================
export const DEFAULT_THRESHOLDS = {
  http_req_duration: ['p(95)<500', 'p(99)<1000'],  // 95%가 500ms 이내, 99%가 1초 이내
  http_req_failed: ['rate<0.01'],                    // 에러율 1% 미만
  http_reqs: ['rate>10'],                            // 초당 10건 이상 처리
};
