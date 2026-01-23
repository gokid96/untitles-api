package com.untitles.domain.auth.controller;

import com.untitles.domain.auth.dto.response.LoginResponse;
import com.untitles.domain.auth.service.AuthService;
import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.dto.request.UserLoginRequestDTO;
import com.untitles.domain.user.service.UserService;
import jakarta.servlet.http.HttpSession;
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
    private final UserService userService;

    /**
     * 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody UserLoginRequestDTO request,
            HttpSession session) {
        LoginResponse response = authService.login(request, session);
        return ResponseEntity.ok(response);
    }

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signup(
            @RequestBody UserCreateRequestDTO request,
            HttpSession session) {
        LoginResponse response = authService.signup(request, session);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다."));
    }

    /**
     * 현재 로그인 상태 확인
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpSession session) {
        // SecurityContext에서 인증 정보 확인
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var authentication = context.getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false));
        }

        var userDetails = (com.untitles.global.security.CustomUserDetails) authentication.getPrincipal();
        
        // DB에서 최신 사용자 정보 조회
        var userInfo = userService.getUserInfo(userDetails.getUserId());

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("authenticated", true);
        response.put("userId", userInfo.getUserId());
        response.put("loginId", userInfo.getLoginId());
        response.put("nickname", userInfo.getNickname());
        response.put("profileImage", userInfo.getProfileImage());

        return ResponseEntity.ok(response);
    }
}
