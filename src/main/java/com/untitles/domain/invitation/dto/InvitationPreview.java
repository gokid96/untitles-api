package com.untitles.domain.invitation.dto;

import com.untitles.domain.workspace.entity.WorkspaceRole;

import java.time.LocalDateTime;

/**
 * 초대 링크(메일)를 연 사람에게 보여주는 정보. 로그인 전에도 볼 수 있으므로 이메일은 일부를 가린다
 */
public record InvitationPreview(
        String workspaceName,
        String inviterNickname,
        WorkspaceRole role,
        String maskedEmail,
        LocalDateTime expiresAt
) {
}
