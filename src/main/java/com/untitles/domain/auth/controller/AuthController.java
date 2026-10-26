package com.untitles.domain.auth.controller;

import com.untitles.domain.auth.dto.request.TokenRefreshRequest;
import com.untitles.domain.auth.dto.response.LoginResponse;
import com.untitles.domain.auth.dto.response.TokenResponse;
import com.untitles.domain.auth.service.AuthService;
import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.dto.request.UserLoginRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final com.untitles.domain.auth.service.PasswordResetService passwordResetService;

    /**
     * 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid UserLoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signup(@RequestBody @Valid UserCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    /**
     * 액세스 토큰 재발급
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody @Valid TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /**
     * 로그아웃 - 클라이언트에서 토큰 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) com.untitles.domain.auth.dto.request.LogoutRequest request) {
        authService.logout(request != null ? request.refreshToken() : null);
        return ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다."));
    }

    /**
     * 비밀번호 재설정 인증 코드 요청. 가입 여부와 관계없이 같은 응답을 준다.
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @RequestBody @Valid com.untitles.domain.auth.dto.request.PasswordResetRequest request) {
        passwordResetService.request(request.email());
        return ResponseEntity.ok(Map.of("message", "등록된 이메일이면 인증번호를 보냈습니다."));
    }

    /**
     * 인증 코드 확인 후 새 비밀번호로 변경 (모든 기기 로그아웃)
     */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, String>> confirmPasswordReset(
            @RequestBody @Valid com.untitles.domain.auth.dto.request.PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request.email(), request.code(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요."));
    }
}
