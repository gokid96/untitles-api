package com.untitles.domain.auth.social;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 웹 리다이렉트 방식 소셜 로그인을 마친 뒤 앱·웹에 넘기는 일회용 코드.
 * 토큰을 주소(URL)에 직접 싣지 않고, 1분 안에 한 번만 토큰으로 바꿀 수 있는 코드만 넘긴다.
 * (API 서버가 한 대라 메모리에 둔다)
 */
@Component
public class LoginCodeStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Cache<String, Long> codes = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, userId);
        return code;
    }

    /** @return 코드의 사용자 ID. 없거나 만료됐거나 이미 썼으면 null */
    public Long consume(String code) {
        if (code == null || code.isBlank()) return null;
        return codes.asMap().remove(code);
    }
}
