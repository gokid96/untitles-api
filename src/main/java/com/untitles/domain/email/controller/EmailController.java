package com.untitles.domain.email.controller;

import com.untitles.domain.email.dto.request.EmailCheckRequest;
import com.untitles.domain.email.dto.request.EmailSendRequest;
import com.untitles.domain.email.dto.request.EmailVerifyRequest;
import com.untitles.domain.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/email")
public class EmailController {

    private final EmailService emailService;

    /**
     * 이메일 중복 확인
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestBody EmailCheckRequest request) {
        boolean isDuplicate = emailService.checkDuplicate(request.email());
        return ResponseEntity.ok(Map.of(
                "available", !isDuplicate,
                "message", isDuplicate ? "이미 사용중인 이메일입니다." : "사용 가능한 이메일입니다."
        ));
    }

    /**
     * 인증번호 발송
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendCode(@RequestBody EmailSendRequest request) {
        emailService.sendVerificationCode(request.email());
        return ResponseEntity.ok(Map.of("message", "인증번호가 발송되었습니다."));
    }

    /**
     * 인증번호 확인
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyCode(@RequestBody EmailVerifyRequest request) {
        boolean verified = emailService.verifyCode(request.email(), request.code());
        return ResponseEntity.ok(Map.of(
                "verified", verified,
                "message", "이메일 인증이 완료되었습니다."
        ));
    }
}