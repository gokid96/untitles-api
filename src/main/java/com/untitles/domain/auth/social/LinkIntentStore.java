package com.untitles.domain.auth.social;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 로그인한 사용자가 내 정보에서 소셜 계정을 "연결"할 때, 리다이렉트(카카오·구글 웹, 애플 웹)로 다녀오는 동안
 * 누구에게 연결할지 기억하는 일회용 표시 (5분).
 */
@Component
public class LinkIntentStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Cache<String, Long> intents = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    public String issue(Long userId) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        intents.put(token, userId);
        return token;
    }

    /** @return 연결할 사용자 ID. 없거나 만료됐거나 이미 썼으면 null */
    public Long consume(String token) {
        if (token == null || token.isBlank()) return null;
        return intents.asMap().remove(token);
    }
}
