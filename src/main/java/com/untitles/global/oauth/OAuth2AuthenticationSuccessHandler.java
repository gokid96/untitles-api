package com.untitles.global.oauth;

import com.untitles.global.jwt.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:5173}")
    private String redirectUri;

    private final JwtProvider jwtProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        String accessToken = jwtProvider.createAccessToken(oAuth2User.getUserId(), oAuth2User.getName());
        String refreshToken = jwtProvider.createRefreshToken(oAuth2User.getUserId(), oAuth2User.getName());

        log.info("OAuth2 로그인 성공: {} (ID: {})", oAuth2User.getName(), oAuth2User.getUserId());

        // 프론트엔드로 토큰 전달
        String targetUrl = redirectUri + "/oauth/callback"
                + "?accessToken=" + accessToken
                + "&refreshToken=" + refreshToken;

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}