package com.untitles.domain.auth.service;

import com.untitles.domain.auth.dto.response.TokenResponse;
import com.untitles.domain.auth.repository.RefreshTokenRepository;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.jwt.JwtProvider;
import com.untitles.support.H2JpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@H2JpaTest
class RefreshTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";

    @Autowired RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, new JwtProvider(SECRET, 60_000, 600_000));
    }

    @Test
    @DisplayName("발급한 리프레시 토큰은 한 번만 쓸 수 있다 (교체)")
    void consumeOnce() {
        TokenResponse tokens = service.issue(1L, "me");

        assertThat(service.consume(tokens.refreshToken())).isEqualTo(1L);
        assertThatThrownBy(() -> service.consume(tokens.refreshToken()))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("로그아웃한 기기의 토큰만 무효화되고 다른 기기는 유지된다")
    void revokeOne() {
        TokenResponse phone = service.issue(1L, "me");
        TokenResponse laptop = service.issue(1L, "me");

        service.revoke(phone.refreshToken());

        assertThatThrownBy(() -> service.consume(phone.refreshToken()))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(service.consume(laptop.refreshToken())).isEqualTo(1L);
    }

    @Test
    @DisplayName("비밀번호 변경 시 그 사용자의 모든 토큰이 무효화된다")
    void revokeAll() {
        TokenResponse a = service.issue(1L, "me");
        TokenResponse b = service.issue(1L, "me");
        TokenResponse other = service.issue(2L, "you");

        service.revokeAll(1L);

        assertThatThrownBy(() -> service.consume(a.refreshToken())).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThatThrownBy(() -> service.consume(b.refreshToken())).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(service.consume(other.refreshToken())).isEqualTo(2L);
    }

    @Test
    @DisplayName("서명이 맞아도 저장되지 않은 토큰(예전에 발급된 토큰)과 액세스 토큰은 거부한다")
    void rejectsUnknownAndAccessTokens() {
        JwtProvider jwt = new JwtProvider(SECRET, 60_000, 600_000);
        String legacy = jwt.createRefreshToken(1L, "me");
        TokenResponse tokens = service.issue(1L, "me");

        assertThatThrownBy(() -> service.consume(legacy)).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThatThrownBy(() -> service.consume(tokens.accessToken())).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThatThrownBy(() -> service.consume(null)).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
