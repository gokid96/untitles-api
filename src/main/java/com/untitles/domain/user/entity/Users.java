package com.untitles.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Users {

    public static final String DELETED_USER_NICKNAME = "탈퇴한 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(unique = true, length = 100)  // 이메일 없이 가입한 계정은 null
    private String email;

    @Column(name = "login_id", unique = true, length = 50)
    private String loginId;

    @Column(length = 255)
    private String password;  // 소셜 로그인으로만 가입한 계정은 null

    // 이메일 소유가 확인됐는지 (가입 인증 코드, 비밀번호 찾기, 인증된 소셜 이메일).
    // 확인된 이메일끼리만 소셜 계정을 자동으로 연결한다.
    @Column(name = "email_verified", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;


    public void updateProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public void updateEmail(String email) {
        boolean same = email != null && email.equalsIgnoreCase(this.email);
        this.email = email;
        if (!same) {
            this.emailVerified = false;  // 바뀐 이메일은 다시 확인 전까지 미확인
        }
    }

    public void markEmailVerified() {
        this.emailVerified = this.email != null;
    }

    public boolean hasPassword() {
        return this.password != null && !this.password.isBlank();
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    // 정적 팩토리 메서드
    /** 이메일로 가입 (인증 코드로 이메일 확인 완료, 아이디 없음) */
    public static Users createWithEmail(String email, String encodedPassword, String nickname) {
        return Users.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .emailVerified(true)
                .build();
    }

    /** 소셜 로그인으로 가입 (비밀번호 없음). 이메일은 제공자가 확인한 경우에만 넣는다 */
    public static Users createSocial(String verifiedEmail, String nickname, String profileImage) {
        return Users.builder()
                .email(verifiedEmail)
                .nickname(nickname)
                .profileImage(profileImage)
                .emailVerified(verifiedEmail != null)
                .build();
    }

    public static Users createLocal(String email, String loginId, String encodedPassword, String nickname) {
        return Users.builder()
                .email(email)
                .loginId(loginId)
                .password(encodedPassword)
                .nickname(nickname)
                .build();
    }
}