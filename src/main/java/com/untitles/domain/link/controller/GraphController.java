package com.untitles.domain.link.controller;

import com.untitles.domain.link.dto.GraphResponse;
import com.untitles.domain.link.service.PostLinkService;
import com.untitles.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/graph")
@RequiredArgsConstructor
public class GraphController {

    private final PostLinkService postLinkService;

    /**
     * 노트 그래프 (글 목록 + 글 사이 링크). 백링크·로컬 그래프는 프론트에서 이 데이터로 계산한다.
     */
    @GetMapping
    public ResponseEntity<GraphResponse> getGraph(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(postLinkService.getGraph(userDetails.getUserId(), workspaceId));
    }
}
