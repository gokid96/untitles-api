package com.untitles.domain.auth.social;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.untitles.domain.user.entity.AuthProvider;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 소셜 로그인 이메일이 기존 계정(이메일 미확인)과 같을 때, 기존 계정 비밀번호로 로그인하면
 * 그 소셜 계정을 연결하도록 잠시 기억해 두는 일회용 연결 요청 (15분).
 * 비밀번호 로그인이 계정 주인임을 증명하므로, 그때 연결하고 이메일도 확인된 것으로 바꾼다.
 */
@Component
public class PendingLinkStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record PendingLink(Long userId, AuthProvider provider, String subject, String email,
                              String appleRefreshToken, String appleClientId) {
        PendingLink withAppleToken(String refreshToken, String clientId) {
            return new PendingLink(userId, provider, subject, email, refreshToken, clientId);
        }
    }

    private final Cache<String, PendingLink> links = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    public String issue(PendingLink link) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        links.put(token, link);
        return token;
    }

    public PendingLink peek(String token) {
        if (token == null || token.isBlank()) return null;
        return links.getIfPresent(token);
    }

    public void attachAppleToken(String token, String refreshToken, String clientId) {
        links.asMap().computeIfPresent(token, (k, v) -> v.withAppleToken(refreshToken, clientId));
    }

    /** @return 연결 요청. 없거나 만료됐거나 이미 썼으면 null */
    public PendingLink consume(String token) {
        if (token == null || token.isBlank()) return null;
        return links.asMap().remove(token);
    }
}
