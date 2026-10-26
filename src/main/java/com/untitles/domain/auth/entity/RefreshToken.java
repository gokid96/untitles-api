package com.untitles.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발급한 리프레시 토큰. 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 * 여기 없는 리프레시 토큰은 서명이 맞아도 거부된다 (로그아웃·비밀번호 변경 시 무효화).
 */
@Entity
@Table(name = "refresh_token", indexes = @Index(name = "idx_refresh_token_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static RefreshToken of(Long userId, String tokenHash, LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }
}
