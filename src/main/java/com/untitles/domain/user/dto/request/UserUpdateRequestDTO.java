package com.untitles.domain.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequestDTO {

    @Size(min = 2, max = 50, message = "닉네임은 2-50자여야 합니다")
    private String nickname;

    private String currentPassword;  // 현재 비밀번호 (비밀번호 변경 시 필수)

    @Size(min = 6, max = 20, message = "비밀번호는 6-20자여야 합니다")
    private String newPassword;  // 새 비밀번호

    @Size(max = 500)
    private String profileImage;
}
