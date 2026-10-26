package com.untitles.domain.auth.dto.request;

/**
 * 로그아웃 요청. 이 기기의 리프레시 토큰을 보내면 서버에서도 무효화한다 (예전 앱은 본문 없이 호출).
 */
public record LogoutRequest(String refreshToken) {
}
