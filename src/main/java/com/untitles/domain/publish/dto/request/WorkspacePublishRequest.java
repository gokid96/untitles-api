package com.untitles.domain.publish.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class WorkspacePublishRequest {
    private Boolean publishAll;          // 워크스페이스 전체공개
    private List<FolderPublishItem> folders;
    private List<Long> publicPostIds;    // 루트 게시글 중 공개할 것들
    private List<Long> excludedRootPostIds; // 전체공개 시 루트 게시글 중 제외할 것들

    @Getter
    @NoArgsConstructor
    public static class FolderPublishItem {
        private Long folderId;
        private Boolean publishAll;          // 폴더 전체공개
        private List<Long> publicPostIds;    // 개별 공개할 게시글 ID
        private List<Long> excludedPostIds;  // 전체공개 시 제외할 게시글 ID
    }
}