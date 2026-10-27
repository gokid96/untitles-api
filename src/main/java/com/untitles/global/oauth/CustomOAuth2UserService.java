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
    private final com.untitles.domain.auth.social.SocialLinkService socialLinkService;
    private final com.untitles.domain.auth.social.LinkIntentStore linkIntentStore;
    private final com.untitles.domain.user.repository.UserRepository userRepository;

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
        // 로그인한 사용자가 내 정보에서 "연결하기"로 시작한 경우: 로그인 대신 그 계정에 연결
        jakarta.servlet.http.HttpServletRequest request = currentRequest();
        String intent = request != null ? OAuthTarget.linkIntent(request) : null;
        if (intent != null) {
            request.setAttribute(OAuthTarget.LINK_MODE_ATTRIBUTE, true);
            request.setAttribute(LINKED_PROVIDER_ATTRIBUTE, provider.name());
            Long userId = linkIntentStore.consume(intent);
            if (userId == null) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("social_link_expired", "연결 요청이 만료됐어요. 다시 시도해주세요.", null));
            }
            try {
                socialLinkService.link(userId, profile);
                var user = userRepository.findById(userId).orElseThrow();
                return new CustomOAuth2User(user, attributes);
            } catch (BusinessException e) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(e.getErrorCode().name().toLowerCase(), e.getErrorCode().getMessage(), null));
            }
        }

        try {
            SocialLoginService.Result result = socialLoginService.login(profile);
            return new CustomOAuth2User(result.user(), attributes);
        } catch (com.untitles.domain.auth.social.SocialLinkRequiredException e) {
            throw new SocialLinkRequiredAuthException(e.getLinkToken(), e.getErrorCode().getMessage());
        } catch (BusinessException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(e.getErrorCode().name().toLowerCase(), e.getErrorCode().getMessage(), null));
        }
    }

    public static final String LINKED_PROVIDER_ATTRIBUTE = "untitles.oauth.linkedProvider";

    private static jakarta.servlet.http.HttpServletRequest currentRequest() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        return attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
                ? servlet.getRequest() : null;
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

    /** 실패 처리기가 연결 토큰을 로그인 화면에 넘길 수 있도록 담아 두는 예외 */
    public static class SocialLinkRequiredAuthException extends OAuth2AuthenticationException {
        private final String linkToken;

        public SocialLinkRequiredAuthException(String linkToken, String message) {
            super(new OAuth2Error("social_email_in_use", message, null), message);
            this.linkToken = linkToken;
        }

        public String getLinkToken() {
            return linkToken;
        }
    }
}
