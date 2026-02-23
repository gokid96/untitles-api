import http from 'k6/http';
import { check, group } from 'k6';
import { BASE_URL, HEADERS, TEST_USER } from './config.js';

/**
 * 공통 유틸리티 함수들
 */

/**
 * 로그인 후 세션 쿠키가 포함된 cookieJar 반환
 */
export function login(jar) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      loginId: TEST_USER.loginId,
      password: TEST_USER.password,
    }),
    {
      headers: HEADERS,
      jar: jar,
    }
  );

  check(res, {
    '로그인 성공 (200)': (r) => r.status === 200,
  });

  return res;
}

/**
 * 인증된 요청용 공통 옵션
 */
export function authParams(jar) {
  return {
    headers: HEADERS,
    jar: jar,
  };
}

/**
 * 랜덤 문자열 생성
 */
export function randomString(length) {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}
