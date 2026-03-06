package com.untitles.domain.publish.dto.response;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.post.dto.response.PostSimpleDTO;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Builder
public class PublicWorkspaceResponse {
    private String workspaceName;
    private String description;
    private List<PublicFolderItem> folders;
    private List<PostSimpleDTO> rootPosts;

    public static PublicWorkspaceResponse of(
            String workspaceName,
            String description,
            List<PublicFolderItem> folders,
            List<PostSimpleDTO> rootPosts) {
        return PublicWorkspaceResponse.builder()
                .workspaceName(workspaceName)
                .description(description)
                .folders(folders)
                .rootPosts(rootPosts)
                .build();
    }

    /**
     * 공개용 폴더 DTO
     * - FolderResponseDTO 와 필드 구조는 동일하게 유지
     * - 내부 DTO 변경이 공개 API 에 자동 반영되는 것을 막기 위해 별도 클래스로 분리
     */
    @Getter
    @Builder
    public static class PublicFolderItem {
        private Long folderId;
        private String name;
        private Long parentId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<PublicFolderItem> children;
        private List<PostSimpleDTO> posts;

        public static PublicFolderItem from(Folder folder, Set<Long> visiblePostIds) {
            List<PostSimpleDTO> filteredPosts = folder.getPosts() != null
                    ? folder.getPosts().stream()
                            .filter(p -> visiblePostIds.contains(p.getPostId()))
                            .map(PostSimpleDTO::from)
                            .collect(Collectors.toList())
                    : List.of();

            List<PublicFolderItem> filteredChildren = folder.getChildren().stream()
                    .map(child -> PublicFolderItem.from(child, visiblePostIds))
                    .collect(Collectors.toList());

            return PublicFolderItem.builder()
                    .folderId(folder.getFolderId())
                    .name(folder.getName())
                    .parentId(folder.getParent() != null ? folder.getParent().getFolderId() : null)
                    .createdAt(folder.getCreatedAt())
                    .updatedAt(folder.getUpdatedAt())
                    .children(filteredChildren)
                    .posts(filteredPosts)
                    .build();
        }
    }

    /**
     * 공개용 게시글 상세 DTO
     * - 비로그인 외부인에게 authorId 등 내부 식별자를 노출하지 않기 위해 별도 유지
     */
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
