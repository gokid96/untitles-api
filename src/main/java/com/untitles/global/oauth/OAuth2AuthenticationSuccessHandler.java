package com.untitles.global.oauth;

import com.untitles.domain.auth.social.LoginCodeStore;
import com.untitles.domain.auth.social.SocialProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 웹 리다이렉트 방식 소셜 로그인 성공: 토큰 대신 1분짜리 일회용 코드를 넘긴다.
 * 웹은 /oauth/callback, 앱은 앱 전용 주소(net.untitles.app://oauth/callback)로 돌려보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:5173}")
    private String redirectUri;

    private final LoginCodeStore loginCodeStore;
    private final SocialProperties socialProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        boolean app = OAuthTarget.isApp(request);
        boolean link = OAuthTarget.isLinkMode(request);
        OAuthTarget.clear(request, response);

        if (link) {
            // 내 정보에서 연결하기: 이미 로그인한 상태이므로 코드 없이 연결 결과만 알린다
            Object provider = request.getAttribute(CustomOAuth2UserService.LINKED_PROVIDER_ATTRIBUTE);
            String base = app ? socialProperties.appRedirectUri() : redirectUri + "/oauth/callback";
            getRedirectStrategy().sendRedirect(request, response, base + "?linked=" + provider);
            return;
        }

        String code = loginCodeStore.issue(oAuth2User.getUserId());

        log.info("OAuth2 로그인 성공: userId={} ({})", oAuth2User.getUserId(), app ? "app" : "web");

        String base = app ? socialProperties.appRedirectUri() : redirectUri + "/oauth/callback";
        getRedirectStrategy().sendRedirect(request, response, base + "?code=" + code);
    }
}
