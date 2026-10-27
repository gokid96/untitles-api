package com.untitles.domain.user.dto.response;

import com.untitles.domain.user.entity.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {

    private Long userId;
    private String email;
    private String loginId;
    private String nickname;
    private String profileImage;
    private boolean emailVerified;
    private boolean hasPassword;          // false면 소셜 로그인으로만 가입한 계정 (비밀번호 설정 가능)
    private java.util.List<String> providers;  // 연결된 소셜 로그인 (KAKAO, APPLE, GOOGLE)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Entity -> DTO 변환 (정적 팩토리 메서드)
    public static UserResponseDTO from(Users user) {
        return from(user, java.util.List.of());
    }

    public static UserResponseDTO from(Users user, java.util.List<String> providers) {
        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .loginId(user.getLoginId())
                .nickname(user.getNickname())
                .profileImage(user.getProfileImage())
                .emailVerified(user.isEmailVerified())
                .hasPassword(user.hasPassword())
                .providers(providers)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}