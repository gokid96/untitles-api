package com.untitles.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.untitles.global.util.PasswordPolicy;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCreateRequestDTO {

    // 이메일 가입이면 필수(인증 완료된 이메일). 아이디 가입(예전 앱)이면 선택
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다")
    private String email;

    // 예전 앱 버전의 아이디 가입에서만 보낸다. 비어 있으면 이메일 가입
    @Pattern(regexp = "^[a-z0-9_-]{4,20}$",
            message = "아이디는 4-20자의 영문 소문자, 숫자, 특수문자(_,-)만 사용 가능합니다")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    private String password;

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(min = 2, max = 50, message = "닉네임은 2-50자여야 합니다")
    private String nickname;
}