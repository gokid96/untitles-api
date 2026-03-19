package com.untitles.domain.auth.controller;

import com.untitles.domain.auth.dto.response.LoginResponse;
import com.untitles.domain.auth.service.AuthService;
import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.dto.request.UserLoginRequestDTO;
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
    public ResponseEntity<LoginResponse> login(@RequestBody UserLoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signup(@RequestBody UserCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    /**
     * 로그아웃 - 클라이언트에서 토큰 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        authService.logout();
        return ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다."));
    }
}