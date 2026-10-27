package com.untitles.domain.user.controller;

import com.untitles.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.untitles.domain.user.dto.request.UserUpdateRequestDTO;
import com.untitles.domain.user.dto.response.UserResponseDTO;
import com.untitles.domain.user.dto.response.UserSearchResponseDTO;
import com.untitles.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    // 조회
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getUserInfo(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserInfo(userDetails.getUserId()));
    }

    // 수정
    @PatchMapping("/me")
    public ResponseEntity<UserResponseDTO> updateUser(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody @Valid UserUpdateRequestDTO request) {
        return ResponseEntity.ok(userService.updateUser(userDetails.getUserId(), request));
    }

    public record EmailCodeRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email(message = "이메일 형식이 올바르지 않습니다")
            @jakarta.validation.constraints.Size(max = 100) String email) {
    }

    public record EmailChangeRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email(message = "이메일 형식이 올바르지 않습니다")
            @jakarta.validation.constraints.Size(max = 100) String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 10) String code) {
    }

    // 이메일 변경: 1) 새 이메일로 인증번호 받기
    @PostMapping("/me/email/code")
    public ResponseEntity<Void> sendEmailChangeCode(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                    @RequestBody @Valid EmailCodeRequest request) {
        userService.sendEmailChangeCode(userDetails.getUserId(), request.email());
        return ResponseEntity.noContent().build();
    }

    // 이메일 변경: 2) 인증번호 확인 후 변경
    @PostMapping("/me/email")
    public ResponseEntity<UserResponseDTO> changeEmail(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                       @RequestBody @Valid EmailChangeRequest request) {
        return ResponseEntity.ok(userService.changeEmail(userDetails.getUserId(), request.email(), request.code()));
    }

    // 탈퇴
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.deleteUser(userDetails.getUserId());
        return ResponseEntity.noContent().build();  // 204 No Content
    }

    // 이메일 또는 닉네임으로 사용자 검색 (초대용)
    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponseDTO>> searchUsers(
            @RequestParam String query,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.searchUsers(query, userDetails.getUserId()));
    }
}
