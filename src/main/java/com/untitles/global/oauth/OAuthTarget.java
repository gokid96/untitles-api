package com.untitles.global.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 웹 리다이렉트 방식 소셜 로그인이 끝난 뒤 돌아갈 곳(웹/앱)을 기억한다.
 * 앱은 /oauth2/authorization/kakao?target=app 으로 시작하고, 끝나면 앱 전용 주소로 돌려보낸다.
 */
public final class OAuthTarget {

    static final String COOKIE = "oauth_target";
    static final String LINK_COOKIE = "oauth_link";
    /** 이번 요청이 "내 정보에서 연결하기"였는지 (성공·실패 처리기가 돌아갈 곳을 고르는 데 씀) */
    public static final String LINK_MODE_ATTRIBUTE = "untitles.oauth.linkMode";

    private OAuthTarget() {
    }

    public static boolean isApp(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && "app".equals(cookie.getValue())) return true;
        }
        return false;
    }

    /** 내 정보에서 연결하기로 시작했으면 그 일회용 표시 */
    public static String linkIntent(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (LINK_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
        }
        return null;
    }

    public static boolean isLinkMode(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(LINK_MODE_ATTRIBUTE)) || linkIntent(request) != null;
    }

    public static void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader("Set-Cookie", cookie(request, COOKIE, "", 0).toString());
        response.addHeader("Set-Cookie", cookie(request, LINK_COOKIE, "", 0).toString());
    }

    private static ResponseCookie cookie(HttpServletRequest request, String name, String value, long maxAge) {
        // 소셜 제공자에서 우리 서버로 돌아오는 것은 최상위 이동(GET)이라 SameSite=Lax 쿠키가 함께 온다
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    /** /oauth2/authorization/* 요청의 target 파라미터를 쿠키로 남기는 필터 */
    public static class Filter extends OncePerRequestFilter {
        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getRequestURI().startsWith("/oauth2/authorization/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            boolean app = "app".equals(request.getParameter("target"));
            response.addHeader("Set-Cookie", cookie(request, COOKIE, app ? "app" : "", app ? 600 : 0).toString());
            String link = request.getParameter("link");
            boolean hasLink = link != null && link.matches("[A-Za-z0-9_-]{16,64}");
            response.addHeader("Set-Cookie", cookie(request, LINK_COOKIE, hasLink ? link : "", hasLink ? 600 : 0).toString());
            chain.doFilter(request, response);
        }
    }
}
