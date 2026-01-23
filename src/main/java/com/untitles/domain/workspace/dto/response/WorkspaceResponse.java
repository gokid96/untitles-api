package com.untitles.domain.workspace.dto.response;

import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.entity.WorkspaceType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WorkspaceResponse(
        Long workspaceId,
        String name,
        String description,
        WorkspaceType type,
        WorkspaceRole myRole,
        int memberCount,
        LocalDateTime createdAt
)
{
    public static WorkspaceResponse from(Workspace workspace, WorkspaceRole myRole) {
        return WorkspaceResponse.builder()
                .workspaceId(workspace.getWorkspaceId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .type(workspace.getType())
                .myRole(myRole)
                .memberCount(workspace.getMembers().size())
                .createdAt(workspace.getCreatedAt())
                .build();
    }
}
