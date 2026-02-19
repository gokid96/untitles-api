package com.untitles.domain.publish.controller;

import com.untitles.domain.publish.dto.response.PublicWorkspaceResponse;
import com.untitles.domain.publish.service.PublicViewService;
import com.untitles.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicViewController {

    private final PublicViewService publicViewService;

    // 공개 워크스페이스 목록 (폴더 트리 + 게시글 제목들)
    @GetMapping("/{slug}")
    public ResponseEntity<PublicWorkspaceResponse> getPublicWorkspace(
            @PathVariable String slug) {
        return ResponseEntity.ok(publicViewService.getPublicWorkspace(slug));
    }

    // 공개 게시글 상세
    @GetMapping("/{slug}/posts/{postId}")
    public ResponseEntity<PublicWorkspaceResponse.PublicPostDetail> getPublicPost(
            @PathVariable String slug,
            @PathVariable Long postId) {
        return ResponseEntity.ok(publicViewService.getPublicPost(slug, postId));
    }
}