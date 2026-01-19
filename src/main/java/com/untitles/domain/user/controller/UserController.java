package com.untitles.domain.user.controller;

import com.untitles.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.untitles.domain.user.dto.request.UserUpdateRequestDTO;
import com.untitles.domain.user.dto.response.UserResponseDTO;
import com.untitles.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    // 조회 @AuthenticationPrincipal 로 인증정보 넘기는거로 수정하기
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getUserInfo(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserInfo(userDetails.getUserId()));
    }

    // 수정
    @PatchMapping("/me")
    public ResponseEntity<UserResponseDTO> updateUser(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody UserUpdateRequestDTO request) {
        return ResponseEntity.ok(userService.updateUser(userDetails.getUserId(), request));
    }

    // 탈퇴 @AuthenticationPrincipal 로 인증정보 넘기는거로 수정하기
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.deleteUser(userDetails.getUserId());
        return ResponseEntity.noContent().build();  // 204 No Content
    }

    // 이메일 또는 닉네임으로 사용자 검색 (초대용)
    @GetMapping("/search")
    public ResponseEntity<List<UserResponseDTO>> searchUsers(
            @RequestParam String query,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.searchUsers(query, userDetails.getUserId()));
    }
}
