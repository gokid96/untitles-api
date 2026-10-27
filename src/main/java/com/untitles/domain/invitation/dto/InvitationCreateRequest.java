package com.untitles.domain.invitation.dto;

import com.untitles.domain.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 초대할 사람: 가입한 사용자(userId) 또는 이메일 */
public record InvitationCreateRequest(
        Long userId,
        @Email(message = "이메일 형식이 올바르지 않습니다") @Size(max = 255) String email,
        @NotNull WorkspaceRole role
) {
    @AssertTrue(message = "초대할 사람을 지정해주세요")
    public boolean isTargetSpecified() {
        return userId != null || (email != null && !email.isBlank());
    }
}
