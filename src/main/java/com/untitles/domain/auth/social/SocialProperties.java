package com.untitles.domain.auth.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 앱·웹의 소셜 로그인 설정 (application.yml의 app.social)
 *
 * @param googleClientIds 구글 ID 토큰의 aud로 허용할 클라이언트 ID (웹, iOS)
 * @param appleClientIds  애플 ID 토큰의 aud로 허용할 ID (앱 번들 ID, 웹 서비스 ID)
 * @param appleTeamId     애플 개발자 팀 ID
 * @param appleKeyId      Sign in with Apple 키 ID
 * @param applePrivateKey Sign in with Apple 키(.p8) 내용. 탈퇴 시 애플 연결 해제에 쓴다
 * @param appRedirectUri  앱에서 카카오 등 웹 방식 로그인을 마친 뒤 돌아갈 주소 (앱 전용 스킴)
 */
@ConfigurationProperties(prefix = "app.social")
public record SocialProperties(List<String> googleClientIds, List<String> appleClientIds,
                               String appleTeamId, String appleKeyId, String applePrivateKey,
                               String appRedirectUri) {

    public SocialProperties {
        googleClientIds = clean(googleClientIds);
        appleClientIds = clean(appleClientIds);
        if (appRedirectUri == null || appRedirectUri.isBlank()) appRedirectUri = "net.untitles.app://oauth/callback";
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }

    public boolean appleKeyConfigured() {
        return appleTeamId != null && !appleTeamId.isBlank()
                && appleKeyId != null && !appleKeyId.isBlank()
                && applePrivateKey != null && !applePrivateKey.isBlank();
    }
}
