package com.untitles.global.oauth;

import com.untitles.domain.auth.social.SocialLoginService;
import com.untitles.domain.auth.social.SocialProfile;
import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 웹 리다이렉트 방식 소셜 로그인(구글·카카오·네이버): 제공자의 사용자 정보로 계정을 찾거나 만든다.
 * 계정 연결 규칙은 SocialLoginService가 맡는다 (확인된 이메일끼리만 연결).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final SocialLoginService socialLoginService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        AuthProvider provider = AuthProvider.valueOf(
                userRequest.getClientRegistration().getRegistrationId().toUpperCase());
        Map<String, Object> attributes = oAuth2User.getAttributes();
        OAuth2UserInfo info = OAuth2UserInfoFactory.getOAuth2UserInfo(provider, attributes);

        if (info.getProviderId() == null || info.getProviderId().isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_provider_id", "소셜 계정 정보를 가져올 수 없습니다.", null));
        }

        SocialProfile profile = new SocialProfile(provider, info.getProviderId(), info.getEmail(),
                emailVerified(provider, attributes), info.getNickname(), info.getProfileImage());
        try {
            SocialLoginService.Result result = socialLoginService.login(profile);
            return new CustomOAuth2User(result.user(), attributes);
        } catch (BusinessException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(e.getErrorCode().name().toLowerCase(), e.getErrorCode().getMessage(), null));
        }
    }

    @SuppressWarnings("unchecked")
    static boolean emailVerified(AuthProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> Boolean.TRUE.equals(attributes.get("email_verified"));
            case KAKAO -> {
                Object account = attributes.get("kakao_account");
                if (!(account instanceof Map<?, ?> map)) yield false;
                Map<String, Object> kakao = (Map<String, Object>) map;
                yield Boolean.TRUE.equals(kakao.get("is_email_verified")) && Boolean.TRUE.equals(kakao.get("is_email_valid"));
            }
            default -> false;  // 네이버 등은 확인 여부를 알 수 없으므로 자동 연결에 쓰지 않는다
        };
    }
}
