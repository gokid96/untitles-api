package com.untitles.domain.auth.social;

import com.untitles.domain.auth.dto.response.LoginResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 소셜 로그인
 * - 앱(및 웹의 애플 로그인): 제공자에게서 받은 ID 토큰을 보내면 검증 후 로그인
 * - 웹 리다이렉트 방식(카카오 등): 로그인 후 받은 일회용 코드를 토큰으로 교환
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/social")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

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

    @PostMapping("/exchange")
    public ResponseEntity<LoginResponse> exchange(@RequestBody @Valid CodeExchangeRequest request) {
        return ResponseEntity.ok(socialAuthService.exchange(request.code()));
    }
}
