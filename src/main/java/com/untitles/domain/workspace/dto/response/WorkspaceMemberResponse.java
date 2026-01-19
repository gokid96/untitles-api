package com.untitles.domain.workspace.dto.response;

import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WorkspaceMemberResponse(
    Long memberId,
    Long userId,
    String email,
    String nickname,
    WorkspaceRole role,
    LocalDateTime joinedAt
) {
    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        return WorkspaceMemberResponse.builder()
            .memberId(member.getId())
            .userId(member.getUser().getUserId())
            .email(member.getUser().getEmail())
            .nickname(member.getUser().getNickname())
            .role(member.getRole())
            .joinedAt(member.getJoinedAt())
            .build();
    }
}