package com.untitles.domain.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private Long userId;
    private String loginId;
    private String nickname;
    private String accessToken;
    private String refreshToken;

    public static LoginResponse of(Long userId, String loginId, String nickname,
                                   String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .userId(userId)
                .loginId(loginId)
                .nickname(nickname)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}