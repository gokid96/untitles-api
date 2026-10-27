package com.untitles.global.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:5173}")
    private String redirectUri;

    private final com.untitles.domain.auth.social.SocialProperties socialProperties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String errorMessage = "소셜 로그인에 실패했습니다.";
        String errorCode = "unknown_error";

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            OAuth2Error error = oauthEx.getError();
            if (error != null) {
                errorCode = error.getErrorCode();
                if (error.getDescription() != null && !error.getDescription().isBlank()) {
                    errorMessage = error.getDescription();
                }
            }
        }

        log.error("OAuth2 로그인 실패: [{}] {}", errorCode, errorMessage);

        String encodedMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        String encodedCode = URLEncoder.encode(errorCode, StandardCharsets.UTF_8);

        boolean app = OAuthTarget.isApp(request);
        boolean linkMode = OAuthTarget.isLinkMode(request);
        OAuthTarget.clear(request, response);
        if (linkMode) {
            // 내 정보에서 연결하기 실패: 로그인 화면이 아니라 연결 결과 화면으로
            String base = app ? socialProperties.appRedirectUri() : redirectUri + "/oauth/callback";
            getRedirectStrategy().sendRedirect(request, response, base + "?mode=link&error=" + encodedMessage);
            return;
        }
        String base = app ? socialProperties.appRedirectUri() : redirectUri + "/login";
        String target = exception instanceof CustomOAuth2UserService.SocialLinkRequiredAuthException link
                // 같은 이메일의 기존 계정: 이메일 로그인 화면으로 (로그인하면 소셜 계정이 연결됨)
                ? base + "?link=" + URLEncoder.encode(link.getLinkToken(), StandardCharsets.UTF_8) + "&error_code=" + encodedCode
                : base + "?error=" + encodedMessage + "&error_code=" + encodedCode;
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}