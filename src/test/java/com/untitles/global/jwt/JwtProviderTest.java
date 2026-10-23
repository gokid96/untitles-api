package com.untitles.global.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 60_000, 600_000);

    @Test
    @DisplayName("액세스 토큰은 액세스 검증만 통과한다")
    void accessToken() {
        String token = jwtProvider.createAccessToken(1L, "tester");

        assertThat(jwtProvider.validateAccessToken(token)).isTrue();
        assertThat(jwtProvider.validateRefreshToken(token)).isFalse();
        assertThat(jwtProvider.getUserId(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("리프레시 토큰으로는 API 인증을 할 수 없다")
    void refreshTokenCannotAuthenticate() {
        String token = jwtProvider.createRefreshToken(1L, "tester");

        assertThat(jwtProvider.validateRefreshToken(token)).isTrue();
        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("다른 키로 서명했거나 형식이 틀린 토큰은 예외 없이 거부한다")
    void invalidTokensRejectedWithoutException() {
        JwtProvider other = new JwtProvider("ffffffffffffffffffffffffffffffffffffffffffffffff", 60_000, 600_000);
        String foreign = other.createAccessToken(1L, "tester");

        assertThat(jwtProvider.validateAccessToken(foreign)).isFalse();
        assertThat(jwtProvider.validateAccessToken("not-a-jwt")).isFalse();
        assertThat(jwtProvider.validateAccessToken("")).isFalse();
    }

    @Test
    @DisplayName("필터는 잘못된 토큰이 와도 인증 없이 다음으로 넘긴다 (500이 아니라 401로 이어짐)")
    void filterPassesThroughInvalidToken() throws Exception {
        JwtProvider other = new JwtProvider("ffffffffffffffffffffffffffffffffffffffffffffffff", 60_000, 600_000);
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + other.createAccessToken(1L, "tester"));
        var chain = new org.springframework.mock.web.MockFilterChain();

        new JwtFilter(jwtProvider).doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
