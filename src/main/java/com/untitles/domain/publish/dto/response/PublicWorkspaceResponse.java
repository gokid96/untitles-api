package com.untitles.domain.publish.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PublicWorkspaceResponse {
    private String workspaceName;
    private String description;
    private List<PublicFolderItem> folders;
    private List<PublicPostItem> rootPosts;

    @Getter
    @Builder
    public static class PublicFolderItem {
        private Long folderId;
        private String name;
        private List<PublicPostItem> posts;
        private List<PublicFolderItem> children;
    }

    @Getter
    @Builder
    public static class PublicPostItem {
        private Long postId;
        private String title;
        private String authorNickname;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class PublicPostDetail {
        private Long postId;
        private String title;
        private String content;
        private String authorNickname;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}