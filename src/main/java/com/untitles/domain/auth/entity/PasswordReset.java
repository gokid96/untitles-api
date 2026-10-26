package com.untitles.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 요청. 사용자당 1건만 유지하고, 인증 코드는 해시로 저장한다.
 */
@Entity
@Table(name = "password_reset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordReset {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    public static PasswordReset of(Long userId, String codeHash, LocalDateTime expiresAt) {
        PasswordReset reset = new PasswordReset();
        reset.userId = userId;
        reset.codeHash = codeHash;
        reset.expiresAt = expiresAt;
        return reset;
    }

    public boolean isUsable(LocalDateTime now) {
        return now.isBefore(expiresAt) && attemptCount < MAX_ATTEMPTS;
    }

    public void increaseAttempt() {
        attemptCount++;
    }
}
