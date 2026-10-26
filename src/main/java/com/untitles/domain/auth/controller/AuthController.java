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
}