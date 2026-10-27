package com.untitles.domain.auth.social;

import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdTokenVerifierTest {

    private final KeyPair keys = rsa();
    private final SocialProperties properties = new SocialProperties(
            List.of("web-client", "ios-client"), List.of("net.untitles.app"), null, null, null, null);
    private final IdTokenVerifier verifier = new IdTokenVerifier(properties,
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build(),
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String token(String iss, String aud, Map<String, Object> claims, Instant exp) {
        return Jwts.builder().issuer(iss).audience().add(aud).and().subject("user-1")
                .claims(claims).issuedAt(new Date()).expiration(Date.from(exp))
                .signWith(keys.getPrivate()).compact();
    }

    @Test
    @DisplayName("구글: 우리 클라이언트 ID로 발급된 토큰이면 사용자 정보를 돌려준다")
    void google() {
        String token = token("https://accounts.google.com", "ios-client",
                Map.of("email", "g@example.com", "email_verified", true, "name", "구글"), Instant.now().plusSeconds(300));

        SocialProfile profile = verifier.verifyGoogle(token);

        assertThat(profile.provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(profile.subject()).isEqualTo("user-1");
        assertThat(profile.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("다른 앱용 토큰(aud 불일치), 만료된 토큰, 다른 발급자는 거부한다")
    void rejectsWrongTokens() {
        String otherApp = token("https://accounts.google.com", "someone-else", Map.of(), Instant.now().plusSeconds(300));
        String expired = token("https://accounts.google.com", "web-client", Map.of(), Instant.now().minusSeconds(600));
        String wrongIssuer = token("https://evil.example.com", "web-client", Map.of(), Instant.now().plusSeconds(300));

        for (String token : List.of(otherApp, expired, wrongIssuer)) {
            assertThatThrownBy(() -> verifier.verifyGoogle(token))
                    .extracting("errorCode").isEqualTo(ErrorCode.SOCIAL_LOGIN_FAILED);
        }
    }

    @Test
    @DisplayName("애플: nonce 해시가 맞아야 하고, 문자열 email_verified도 인식한다")
    void appleNonce() {
        String token = token("https://appleid.apple.com", "net.untitles.app",
                Map.of("nonce", IdTokenVerifier.sha256Hex("raw-nonce"), "email", "x@privaterelay.appleid.com",
                        "email_verified", "true"), Instant.now().plusSeconds(300));

        var verified = verifier.verifyApple(token, "raw-nonce", "홍길동");
        assertThat(verified.profile().emailVerified()).isTrue();
        assertThat(verified.profile().nickname()).isEqualTo("홍길동");
        assertThat(verified.clientId()).isEqualTo("net.untitles.app");

        assertThatThrownBy(() -> verifier.verifyApple(token, "other-nonce", null))
                .extracting("errorCode").isEqualTo(ErrorCode.SOCIAL_LOGIN_FAILED);
    }
}
