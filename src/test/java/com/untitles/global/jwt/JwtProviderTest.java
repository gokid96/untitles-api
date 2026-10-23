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
}
