package com.untitles.global.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

public class NaverOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String, Object> attributes;

    @SuppressWarnings("unchecked")
    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        Object response = attributes.get("response");
        if (response == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_response", "네이버 응답이 올바르지 않습니다.", null)
            );
        }
        this.attributes = (Map<String, Object>) response;
    }

    @Override
    public String getProviderId() {
        return (String) attributes.get("id");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getNickname() {
        String nickname = (String) attributes.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            nickname = (String) attributes.get("name");
        }
        return nickname;
    }

    @Override
    public String getProfileImage() {
        return (String) attributes.get("profile_image");
    }
}