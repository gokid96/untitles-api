package com.untitles.domain.workspace.dto.request;

import com.untitles.domain.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 초대할 사용자는 userId 또는 email로 지정 (userId 우선)
 * 이메일 없이 가입한 사용자도 있으므로 userId 사용을 권장
 */
public record MemberInviteRequest(
    Long userId,
    String email,
    @NotNull WorkspaceRole role
) {
    @AssertTrue(message = "초대할 사용자를 지정해주세요")
    public boolean isTargetSpecified() {
        return userId != null || (email != null && !email.isBlank());
    }
}
