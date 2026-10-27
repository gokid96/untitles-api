package com.untitles.domain.auth.social;

import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * 앱(또는 웹의 애플 로그인)이 받아 온 ID 토큰을 제공자의 공개키로 검증한다.
 * 서명, 발급자(iss), 대상(aud: 우리 앱의 클라이언트 ID), 만료를 모두 확인한다.
 */
@Slf4j
@Component
public class IdTokenVerifier {

    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String APPLE_JWKS = "https://appleid.apple.com/auth/keys";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final SocialProperties properties;
    private final JwtDecoder googleDecoder;
    private final JwtDecoder appleDecoder;

    public IdTokenVerifier(SocialProperties properties) {
        this(properties,
                NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build(),
                NimbusJwtDecoder.withJwkSetUri(APPLE_JWKS).build());
    }

    IdTokenVerifier(SocialProperties properties, NimbusJwtDecoder google, NimbusJwtDecoder apple) {
        this.properties = properties;
        google.setJwtValidator(validator(GOOGLE_ISSUERS, properties.googleClientIds()));
        apple.setJwtValidator(validator(Set.of(APPLE_ISSUER), properties.appleClientIds()));
        this.googleDecoder = google;
        this.appleDecoder = apple;
    }

    public SocialProfile verifyGoogle(String idToken) {
        if (properties.googleClientIds().isEmpty()) throw new BusinessException(ErrorCode.SOCIAL_NOT_CONFIGURED);
        Jwt jwt = decode(googleDecoder, idToken);
        return new SocialProfile(AuthProvider.GOOGLE, jwt.getSubject(), jwt.getClaimAsString("email"),
                isTrue(jwt.getClaim("email_verified")), jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
    }

    /**
     * @param rawNonce 앱이 애플 로그인 요청에 쓴 nonce 원문 (애플은 SHA-256 해시를 토큰에 넣는다). 없으면 확인 생략
     * @param name     애플은 이름을 첫 로그인 때만 앱에 주므로 앱이 함께 보낸다
     */
    public VerifiedApple verifyApple(String idToken, String rawNonce, String name) {
        if (properties.appleClientIds().isEmpty()) throw new BusinessException(ErrorCode.SOCIAL_NOT_CONFIGURED);
        Jwt jwt = decode(appleDecoder, idToken);
        if (rawNonce != null && !rawNonce.isBlank()) {
            String expected = sha256Hex(rawNonce);
            if (!expected.equals(jwt.getClaimAsString("nonce"))) {
                throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }
        }
        String audience = jwt.getAudience().isEmpty() ? null : jwt.getAudience().get(0);
        SocialProfile profile = new SocialProfile(AuthProvider.APPLE, jwt.getSubject(), jwt.getClaimAsString("email"),
                isTrue(jwt.getClaim("email_verified")), name, null);
        return new VerifiedApple(profile, audience);
    }

    /** 애플 검증 결과와, 토큰의 aud(앱 번들 ID 또는 웹 서비스 ID: 이후 애플 API 호출에 같은 값을 써야 함) */
    public record VerifiedApple(SocialProfile profile, String clientId) {
    }

    private Jwt decode(JwtDecoder decoder, String token) {
        if (token == null || token.isBlank()) throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
        try {
            return decoder.decode(token);
        } catch (JwtException e) {
            log.warn("ID 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }
    }

    private static OAuth2TokenValidator<Jwt> validator(Set<String> issuers, List<String> audiences) {
        OAuth2TokenValidator<Jwt> issuer = jwt -> issuers.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "issuer", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                && jwt.getAudience().stream().anyMatch(audiences::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience", null));
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience);
    }

    // 구글은 boolean, 애플은 "true" 문자열로 줄 때가 있다
    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
