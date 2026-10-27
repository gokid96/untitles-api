package com.untitles.domain.auth.social;

import com.untitles.domain.auth.dto.response.LoginResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.untitles.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.*;

/**
 * 소셜 로그인
 * - 앱(및 웹의 애플 로그인): 제공자에게서 받은 ID 토큰을 보내면 검증 후 로그인
 * - 웹 리다이렉트 방식(카카오 등): 로그인 후 받은 일회용 코드를 토큰으로 교환
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/social")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;
    private final SocialLinkService socialLinkService;
    private final LinkIntentStore linkIntentStore;
    private final ObjectMapper objectMapper;

    @Value("${app.oauth2.redirect-uri:http://localhost:5173}")
    private String webBaseUrl;

    public record GoogleLoginRequest(@NotBlank @Size(max = 4096) String idToken) {
    }

    public record AppleLoginRequest(@NotBlank @Size(max = 4096) String idToken,
                                    @Size(max = 1024) String authorizationCode,
                                    @Size(max = 256) String nonce,
                                    @Size(max = 50) String name) {
    }

    public record CodeExchangeRequest(@NotBlank @Size(max = 128) String code) {
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> google(@RequestBody @Valid GoogleLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.loginWithGoogle(request.idToken()));
    }

    @PostMapping("/apple")
    public ResponseEntity<LoginResponse> apple(@RequestBody @Valid AppleLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.loginWithApple(
                request.idToken(), request.authorizationCode(), request.nonce(), request.name()));
    }

    public record LinkTokenRequest(@NotBlank @Size(max = 128) String token) {
    }

    /** 소셜 로그인이 기존 계정과 겹쳐 연결이 필요한 경우, 로그인 화면에 채울 이메일과 제공자 */
    @PostMapping("/link")
    public ResponseEntity<SocialAuthService.PendingLinkInfo> pendingLink(@RequestBody @Valid LinkTokenRequest request) {
        return ResponseEntity.ok(socialAuthService.pendingLink(request.token()));
    }

    @PostMapping("/exchange")
    public ResponseEntity<LoginResponse> exchange(@RequestBody @Valid CodeExchangeRequest request) {
        return ResponseEntity.ok(socialAuthService.exchange(request.code()));
    }

    /**
     * 웹 애플 로그인 콜백. 애플이 브라우저를 통해 form_post로 결과를 보내면 확인 후
     * 웹의 /oauth/callback 으로 일회용 코드와 state를 넘긴다 (웹이 state를 확인).
     */
    @PostMapping(value = "/apple/callback", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> appleCallback(@RequestParam(value = "id_token", required = false) String idToken,
                                              @RequestParam(value = "code", required = false) String code,
                                              @RequestParam(value = "user", required = false) String user,
                                              @RequestParam(value = "state", required = false) String state,
                                              @RequestParam(value = "error", required = false) String error) {
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(webBaseUrl);

        // 내 정보에서 연결하기로 시작했으면 state 끝에 ".일회용표시"가 붙어 있다
        String intent = state != null && state.contains(".") ? state.substring(state.indexOf('.') + 1) : null;
        if (intent != null) {
            target.path("/oauth/callback").queryParam("provider", "apple").queryParam("state", state);
            if (error != null || idToken == null) {
                if (error != null && !"user_cancelled_authorize".equals(error)) {
                    target.queryParam("mode", "link").queryParam("error", "애플 계정 연결에 실패했습니다.");
                } else {
                    target.queryParam("mode", "link").queryParam("cancelled", "1");
                }
                return redirect(target);
            }
            Long userId = linkIntentStore.consume(intent);
            try {
                if (userId == null) throw new BusinessException(com.untitles.global.exception.ErrorCode.SOCIAL_LINK_EXPIRED);
                socialLinkService.linkApple(userId, idToken, code, null);
                target.queryParam("linked", "APPLE");
            } catch (BusinessException e) {
                target.queryParam("mode", "link").queryParam("error", e.getErrorCode().getMessage());
            } catch (Exception e) {
                log.warn("애플 계정 연결 실패", e);
                target.queryParam("mode", "link").queryParam("error", "애플 계정 연결에 실패했습니다.");
            }
            return redirect(target);
        }

        if (error != null || idToken == null) {
            // 사용자가 취소한 경우 등: 로그인 화면으로 (취소는 메시지 없이)
            target.path("/login");
            if (error != null && !"user_cancelled_authorize".equals(error)) {
                target.queryParam("error", "애플 로그인에 실패했습니다. 다시 시도해주세요.");
            }
            return redirect(target);
        }
        try {
            String loginCode = socialAuthService.loginWithAppleRedirect(idToken, code, appleName(user));
            target.path("/oauth/callback").queryParam("code", loginCode).queryParam("provider", "apple");
            if (state != null) target.queryParam("state", state);
        } catch (SocialLinkRequiredException e) {
            // 같은 이메일의 기존 계정: 이메일 로그인 화면으로 (로그인하면 애플 계정이 연결됨)
            target.path("/login").queryParam("link", e.getLinkToken());
        } catch (BusinessException e) {
            target.path("/login").queryParam("error", e.getErrorCode().getMessage());
        } catch (Exception e) {
            log.warn("애플 웹 로그인 실패", e);
            target.path("/login").queryParam("error", "애플 로그인에 실패했습니다. 다시 시도해주세요.");
        }
        return redirect(target);
    }

    // 애플은 이름을 첫 로그인 때만 user JSON으로 보낸다: {"name":{"firstName":"","lastName":""},"email":""}
    private String appleName(String userJson) {
        if (userJson == null || userJson.isBlank()) return null;
        try {
            JsonNode name = objectMapper.readTree(userJson).path("name");
            String full = (name.path("lastName").asText("") + name.path("firstName").asText("")).trim();
            return full.isEmpty() ? null : full;
        } catch (Exception e) {
            return null;
        }
    }

    private static ResponseEntity<Void> redirect(UriComponentsBuilder target) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(target.encode().build().toUri());
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);  // POST 다음 GET으로 이동
    }
}
