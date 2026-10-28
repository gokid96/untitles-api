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
    /** 멤버·폴더·노트 수 (노트 본문을 읽지 않으려고 따로 센 값) */
    public record Counts(int members, int folders, int posts) {
        public static final Counts EMPTY = new Counts(0, 0, 0);
    }

    public static WorkspaceResponse from(Workspace workspace, WorkspaceRole myRole, Counts counts) {
        return WorkspaceResponse.builder()
                .workspaceId(workspace.getWorkspaceId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .type(workspace.getType())
                .myRole(myRole)
                .memberCount(counts.members())
                .memberLimit(5)
                .folderCount(counts.folders())
                .folderLimit(20)
                .postCount(counts.posts())
                .postLimit(50)
                .createdAt(workspace.getCreatedAt())
                .build();
    }
}