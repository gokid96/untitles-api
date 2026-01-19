package com.untitles.domain.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long userId;
    private String loginId;
    private String nickname;

    public static TokenResponse of(String accessToken, String refreshToken, 
                                   Long userId, String loginId, String nickname) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(userId)
                .loginId(loginId)
                .nickname(nickname)
                .build();
    }
}
