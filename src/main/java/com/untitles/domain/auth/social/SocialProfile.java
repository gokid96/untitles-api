package com.untitles.domain.auth.social;

import com.untitles.domain.user.entity.AuthProvider;

/**
 * 소셜 제공자가 확인해 준 사용자 정보
 *
 * @param subject       제공자 안에서 변하지 않는 사용자 ID
 * @param email         제공자가 알려준 이메일 (없을 수 있음)
 * @param emailVerified 제공자가 그 이메일의 소유를 확인했는지
 */
public record SocialProfile(AuthProvider provider, String subject, String email, boolean emailVerified,
                            String nickname, String profileImage) {

    /** 프로필 사진 주소. https 페이지에서 깨지지 않도록 http는 https로 (카카오는 http 주소를 주기도 함) */
    public String safeProfileImage() {
        if (profileImage == null || profileImage.isBlank()) return null;
        String url = profileImage.trim();
        if (url.startsWith("http://")) url = "https://" + url.substring("http://".length());
        return url.startsWith("https://") && url.length() <= 500 ? url : null;
    }
}
