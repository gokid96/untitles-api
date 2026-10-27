package com.untitles.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "이메일을 입력해주세요") @Email(message = "이메일 형식이 올바르지 않습니다") String email,
        @NotBlank(message = "인증번호를 입력해주세요") @Pattern(regexp = "\\d{6}", message = "인증번호는 6자리 숫자입니다") String code,
        @NotBlank(message = "새 비밀번호를 입력해주세요")
        @Pattern(regexp = com.untitles.global.util.PasswordPolicy.REGEX, message = com.untitles.global.util.PasswordPolicy.MESSAGE) String newPassword
) {
}
