package com.untitles.global.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

    // 토큰 종류 클레임: 리프레시 토큰을 액세스 토큰처럼 쓰지 못하게 구분한다
    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * Access Token 생성
     */
    public String createAccessToken(Long userId, String loginId) {
        return createToken(userId, loginId, accessTokenExpiration, ACCESS_TYPE);
    }

    /**
     * Refresh Token 생성
     */
    public String createRefreshToken(Long userId, String loginId) {
        return createToken(userId, loginId, refreshTokenExpiration, REFRESH_TYPE);
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    private String createToken(Long userId, String loginId, long expiration, String type) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("loginId", loginId)
                .claim(TYPE_CLAIM, type)
                .id(java.util.UUID.randomUUID().toString())  // 같은 시각에 발급해도 토큰이 겹치지 않도록
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰에서 userId 추출
     */
    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    /**
     * 토큰에서 loginId 추출
     */
    public String getLoginId(String token) {
        return getClaims(token).get("loginId", String.class);
    }

    /**
     * 액세스 토큰 검증 (API 인증용)
     */
    public boolean validateAccessToken(String token) {
        return validateToken(token, ACCESS_TYPE);
    }

    /**
     * 리프레시 토큰 검증 (토큰 재발급용)
     */
    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH_TYPE);
    }

    private boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = getClaims(token);
            if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
                log.warn("토큰 종류 불일치: expected={}", expectedType);
                return false;
            }
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("만료된 JWT 토큰: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치(SignatureException), 형식 오류 등 모든 JWT 오류를 401로 처리
            // (이전에는 서명 오류가 잡히지 않아 필터 밖으로 던져져 500이 났음)
            log.warn("유효하지 않은 JWT 토큰: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return false;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
