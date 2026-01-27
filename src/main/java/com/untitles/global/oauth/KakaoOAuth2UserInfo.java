package com.untitles.global.oauth;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String, Object> attributes;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

    @SuppressWarnings("unchecked")
    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        this.kakaoAccount = attributes.get("kakao_account") != null
                ? (Map<String, Object>) attributes.get("kakao_account")
                : Map.of();
        this.profile = kakaoAccount.get("profile") != null
                ? (Map<String, Object>) kakaoAccount.get("profile")
                : Map.of();
    }

    @Override
    public String getProviderId() {
        Object id = attributes.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    @Override
    public String getEmail() {
        Boolean hasEmail = (Boolean) kakaoAccount.get("has_email");
        Boolean isEmailValid = (Boolean) kakaoAccount.get("is_email_valid");

        if (hasEmail == null || !hasEmail) {
            return null;
        }

        if (isEmailValid != null && !isEmailValid) {
            return null;
        }

        return (String) kakaoAccount.get("email");
    }

    @Override
    public String getNickname() {
        return (String) profile.get("nickname");
    }

    @Override
    public String getProfileImage() {
        Boolean isDefaultImage = (Boolean) profile.get("is_default_image");
        if (isDefaultImage != null && isDefaultImage) {
            return null;
        }
        return (String) profile.get("profile_image_url");
    }
}