package com.untitles.domain.user.dto.response;

import com.untitles.domain.user.entity.Users;

/**
 * 멤버 초대용 사용자 검색 결과
 * 다른 사용자에게 보여주므로 로그인 아이디는 빼고 이메일은 일부를 가린다
 */
public record UserSearchResponseDTO(
        Long userId,
        String nickname,
        String email,
        String profileImage
) {
    public static UserSearchResponseDTO from(Users user) {
        return new UserSearchResponseDTO(
                user.getUserId(),
                user.getNickname(),
                maskEmail(user.getEmail()),
                user.getProfileImage()
        );
    }

    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }
}
