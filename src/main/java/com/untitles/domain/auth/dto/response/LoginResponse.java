package com.untitles.domain.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    
    private Long userId;
    private String loginId;
    private String nickname;
    
    public static LoginResponse of(Long userId, String loginId, String nickname) {
        return LoginResponse.builder()
                .userId(userId)
                .loginId(loginId)
                .nickname(nickname)
                .build();
    }
}
