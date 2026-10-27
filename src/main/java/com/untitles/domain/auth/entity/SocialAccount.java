package com.untitles.domain.auth.entity;

import com.untitles.domain.user.entity.AuthProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 계정에 연결된 소셜 로그인. 한 계정에 여러 제공자(카카오·애플·구글)를 연결할 수 있다.
 * 로그인할 때는 이메일이 아니라 (제공자, 제공자의 사용자 ID)로 계정을 찾는다.
 */
@Entity
@Table(name = "social_account",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "subject"}),
        indexes = @Index(columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(length = 100)
    private String email;

    // 애플: 탈퇴할 때 애플 쪽 연결을 끊는 데 쓰는 토큰과, 그 토큰을 받은 client_id
    @Column(name = "apple_refresh_token", length = 500)
    private String appleRefreshToken;

    @Column(name = "apple_client_id", length = 100)
    private String appleClientId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static SocialAccount of(Long userId, AuthProvider provider, String subject, String email) {
        SocialAccount account = new SocialAccount();
        account.userId = userId;
        account.provider = provider;
        account.subject = subject;
        account.email = email;
        return account;
    }

    public void updateAppleToken(String refreshToken, String clientId) {
        this.appleRefreshToken = refreshToken;
        this.appleClientId = clientId;
    }
}
