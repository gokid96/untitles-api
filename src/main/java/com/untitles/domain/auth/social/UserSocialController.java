package com.untitles.domain.auth.social;

import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 내 정보 > 로그인 방법: 소셜 계정 연결·해제
 * - 앱(구글·애플): ID 토큰으로 바로 연결
 * - 리다이렉트(카카오, 웹 구글·애플): link-intent로 일회용 표시를 받아 소셜 로그인 주소에 붙여 다녀온다
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/social")
public class UserSocialController {

    private final SocialLinkService socialLinkService;
    private final LinkIntentStore linkIntentStore;

    public record GoogleLinkRequest(@NotBlank @Size(max = 4096) String idToken) {
    }

    public record AppleLinkRequest(@NotBlank @Size(max = 4096) String idToken,
                                   @Size(max = 1024) String authorizationCode,
                                   @Size(max = 256) String nonce) {
    }

    @PostMapping("/link-intent")
    public ResponseEntity<Map<String, String>> linkIntent(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(Map.of("intent", linkIntentStore.issue(user.getUserId())));
    }

    @PostMapping("/google")
    public ResponseEntity<Void> linkGoogle(@AuthenticationPrincipal CustomUserDetails user,
                                           @RequestBody @Valid GoogleLinkRequest request) {
        socialLinkService.linkGoogle(user.getUserId(), request.idToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/apple")
    public ResponseEntity<Void> linkApple(@AuthenticationPrincipal CustomUserDetails user,
                                          @RequestBody @Valid AppleLinkRequest request) {
        socialLinkService.linkApple(user.getUserId(), request.idToken(), request.authorizationCode(), request.nonce());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal CustomUserDetails user, @PathVariable String provider) {
        AuthProvider parsed;
        try {
            parsed = AuthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        socialLinkService.unlink(user.getUserId(), parsed);
        return ResponseEntity.noContent().build();
    }
}
