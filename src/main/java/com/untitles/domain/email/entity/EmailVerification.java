package com.untitles.domain.email.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attemptCount;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static EmailVerification create(String email, String code, int expirationMinutes) {
        EmailVerification verification = new EmailVerification();
        verification.email = email;
        verification.code = code;
        verification.verified = false;
        verification.expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);
        return verification;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public boolean isAttemptExceeded() {
        return this.attemptCount >= MAX_ATTEMPTS;
    }

    public void increaseAttempt() {
        this.attemptCount++;
    }

    public void verify() {
        this.verified = true;
    }
}