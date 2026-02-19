package com.untitles.domain.publish.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PublishSettingResponse {
    private Boolean publishAll;
    private String publicSlug;
    private String publicUrl;
    private List<FolderPublishInfo> folders;
    private List<PostPublishInfo> rootPosts;

    @Getter
    @Builder
    public static class FolderPublishInfo {
        private Long folderId;
        private String name;
        private Boolean publishAll;
        private List<PostPublishInfo> posts;
        private List<FolderPublishInfo> children;  // 하위 폴더
    }

    @Getter
    @Builder
    public static class PostPublishInfo {
        private Long postId;
        private String title;
        private Boolean isPublic;
        private Boolean isExcluded;
    }
}