package com.untitles.domain.auth.social;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * 애플 REST API: 로그인 때 받은 authorization code를 리프레시 토큰으로 바꿔 두었다가,
 * 탈퇴할 때 그 토큰으로 애플 쪽 연결을 끊는다 (App Store 심사 지침 5.1.1(v)).
 * 키(.p8)가 설정되지 않았으면 아무것도 하지 않는다.
 */
@Slf4j
@Component
public class AppleAuthClient {

    private static final String APPLE = "https://appleid.apple.com";

    private final SocialProperties properties;
    private final RestClient restClient;

    public AppleAuthClient(SocialProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(APPLE).build();
    }

    /** @return 애플 리프레시 토큰. 키가 없거나 실패하면 null */
    public String exchangeCode(String authorizationCode, String clientId) {
        if (!properties.appleKeyConfigured() || authorizationCode == null || authorizationCode.isBlank() || clientId == null) {
            return null;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", authorizationCode);
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret(clientId));
            Map<?, ?> body = restClient.post().uri("/auth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object token = body == null ? null : body.get("refresh_token");
            return token == null ? null : token.toString();
        } catch (Exception e) {
            log.warn("애플 토큰 교환 실패: {}", e.getMessage());
            return null;
        }
    }

    /** 탈퇴 시 애플 연결 해제. 실패해도 탈퇴는 진행한다 */
    public void revoke(String refreshToken, String clientId) {
        if (!properties.appleKeyConfigured() || refreshToken == null || clientId == null) return;
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret(clientId));
            form.add("token", refreshToken);
            form.add("token_type_hint", "refresh_token");
            restClient.post().uri("/auth/revoke")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("애플 연결 해제 실패: {}", e.getMessage());
        }
    }

    // 애플이 요구하는 client_secret: 개발자 키로 서명한 ES256 JWT (최대 6개월, 여기서는 5분)
    String clientSecret(String clientId) throws Exception {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(properties.appleKeyId()).and()
                .issuer(properties.appleTeamId())
                .subject(clientId)
                .audience().add(APPLE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                .signWith(privateKey(), Jwts.SIG.ES256)
                .compact();
    }

    private PrivateKey privateKey() throws Exception {
        String pem = properties.applePrivateKey()
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }
}
