package com.untitles.domain.auth.social;

import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.Getter;

/**
 * 소셜 계정의 이메일로 이미 가입된 계정이 있지만 그 계정의 이메일이 확인되지 않아 자동 연결하지 못한 경우.
 * 클라이언트는 이메일 로그인 화면으로 넘기고, 로그인할 때 linkToken을 함께 보내면 연결된다.
 */
@Getter
public class SocialLinkRequiredException extends BusinessException {

    private final String linkToken;
    private final String email;
    private final AuthProvider provider;

    public SocialLinkRequiredException(String linkToken, String email, AuthProvider provider) {
        super(ErrorCode.SOCIAL_EMAIL_IN_USE);
        this.linkToken = linkToken;
        this.email = email;
        this.provider = provider;
    }
}
