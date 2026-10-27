package com.untitles.domain.invitation.dto;

import com.untitles.domain.workspace.entity.WorkspaceRole;

import java.time.LocalDateTime;

/**
 * 초대 정보
 * - 워크스페이스 관리자에게: 누구를 초대했는지 (invitee*, email)
 * - 초대받은 사람에게: 어느 워크스페이스에 누가 초대했는지 (workspace*, inviter*), 수락·거절에 쓸 token
 */
public record InvitationResponse(
        Long invitationId,
        String token,
        Long workspaceId,
        String workspaceName,
        String inviterNickname,
        Long inviteeUserId,
        String inviteeNickname,
        String inviteeProfileImage,
        String email,
        WorkspaceRole role,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
