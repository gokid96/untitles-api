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

        // 멤버
        int memberCount,
        int memberLimit,

        // 폴더
        int folderCount,
        int folderLimit,

        // 게시글
        int postCount,
        int postLimit,

        LocalDateTime createdAt
) {
    public static WorkspaceResponse from(Workspace workspace, WorkspaceRole myRole) {
        return WorkspaceResponse.builder()
                .workspaceId(workspace.getWorkspaceId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .type(workspace.getType())
                .myRole(myRole)
                .memberCount(workspace.getMembers().size())
                .memberLimit(5)
                .folderCount(workspace.getFolders().size())
                .folderLimit(20)
                .postCount(workspace.getPosts().size())
                .postLimit(50)
                .createdAt(workspace.getCreatedAt())
                .build();
    }
}