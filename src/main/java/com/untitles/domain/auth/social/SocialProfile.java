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
}
