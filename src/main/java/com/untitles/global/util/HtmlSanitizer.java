package com.untitles.global.util;

import lombok.extern.slf4j.Slf4j;
import org.owasp.validator.html.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * HTML 콘텐츠에서 XSS 공격을 방지하기 위한 Sanitizer
 * OWASP AntiSamy 라이브러리 사용
 */
@Slf4j
@Component
public class HtmlSanitizer {

    private final AntiSamy antiSamy;
    private final Policy policy;

    public HtmlSanitizer() {
        try {
            // 커스텀 정책 파일 로드 (리소스에서)
            InputStream policyStream = getClass().getResourceAsStream("/antisamy-policy.xml");
            if (policyStream != null) {
                this.policy = Policy.getInstance(policyStream);
            } else {
                // 기본 정책 사용 (slashdot - 비교적 관대한 정책)
                this.policy = Policy.getInstance(
                    getClass().getResourceAsStream("/antisamy-slashdot.xml")
                );
            }
            this.antiSamy = new AntiSamy();
        } catch (PolicyException e) {
            log.error("AntiSamy 정책 로드 실패", e);
            throw new RuntimeException("HTML Sanitizer 초기화 실패", e);
        }
    }

    /**
     * HTML 콘텐츠를 정화(sanitize)
     * 허용되지 않은 태그와 속성을 제거하고 안전한 HTML만 반환
     *
     * @param html 정화할 HTML 문자열
     * @return 정화된 HTML 문자열
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }

        try {
            CleanResults results = antiSamy.scan(html, policy);
            
            // 경고 로그 (디버깅용)
            if (!results.getErrorMessages().isEmpty()) {
                log.debug("HTML Sanitizer 경고: {}", results.getErrorMessages());
            }
            
            return results.getCleanHTML();
        } catch (ScanException | PolicyException e) {
            log.error("HTML Sanitize 실패", e);
            // 실패 시 모든 HTML 태그 제거 (안전한 fallback)
            return html.replaceAll("<[^>]*>", "");
        }
    }
}
