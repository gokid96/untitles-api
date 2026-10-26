package com.untitles.global.config;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Rate Limiting Filter
 * 요청 경로에 따라 다른 제한을 적용
 */
@Slf4j
@Component
@Order(1)  // 가장 먼저 실행
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    private final Cache<String, Bucket> rateLimitBuckets;

    // IP별 버킷 타입별 캐시 키 생성
    private static final String LOGIN_PREFIX = "login:";
    private static final String EMAIL_PREFIX = "email:";
    private static final String SIGNUP_PREFIX = "signup:";
    private static final String UPLOAD_PREFIX = "upload:";
    private static final String DEFAULT_PREFIX = "default:";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIp(httpRequest);
        String path = httpRequest.getRequestURI();

        Bucket bucket = resolveBucket(clientIp, path);

        if (bucket.tryConsume(1)) {
            // 요청 허용
            chain.doFilter(request, response);
        } else {
            // Rate Limit 초과
            log.warn("Rate limit exceeded for IP: {}, path: {}", clientIp, path);
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write(
                    "{\"status\":\"error\",\"code\":429,\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}"
            );
        }
    }

    /**
     * 요청 경로에 따라 적절한 Bucket 반환
     */
    private Bucket resolveBucket(String clientIp, String path) {
        if (path.contains("/auth/login")) {
            String key = LOGIN_PREFIX + clientIp;
            return rateLimitBuckets.get(key, k -> RateLimitConfig.createLoginBucket());  // 분당 10회
        } else if (path.contains("/auth/signup")) {
            return rateLimitBuckets.get(SIGNUP_PREFIX + clientIp, k -> RateLimitConfig.createSignupBucket());  // 시간당 5회
        } else if (path.contains("/images/")) {
            return rateLimitBuckets.get(UPLOAD_PREFIX + clientIp, k -> RateLimitConfig.createUploadBucket());  // 시간당 60회
        } else if (path.contains("/email/send") || path.contains("/password-reset/request")) {
            String key = EMAIL_PREFIX + clientIp;
            return rateLimitBuckets.get(key, k -> RateLimitConfig.createEmailBucket());  // 시간당 5회
        } else {
            String key = DEFAULT_PREFIX + clientIp;
            return rateLimitBuckets.get(key, k -> RateLimitConfig.createDefaultBucket());  // 분당 60회
        }
    }
    
    /**
     * 클라이언트 IP 추출
     * X-Forwarded-For는 클라이언트가 임의로 보낼 수 있으므로 직접 읽지 않는다.
     * server.forward-headers-strategy=native 설정으로 Tomcat RemoteIpValve가
     * 신뢰하는 내부 프록시(Caddy, cloudflared)를 건너뛰고 실제 클라이언트 IP를 remoteAddr에 넣어준다.
     */
    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
