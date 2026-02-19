package com.untitles.domain.publish.controller;

import com.untitles.domain.publish.dto.request.WorkspacePublishRequest;
import com.untitles.domain.publish.dto.response.PublishSettingResponse;
import com.untitles.domain.publish.service.PublishService;
import com.untitles.global.dto.ApiResponse;
import com.untitles.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/publish")
@RequiredArgsConstructor
public class PublishController {

    private final PublishService publishService;

    @GetMapping
    public ResponseEntity<PublishSettingResponse> getPublishSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(
                publishService.getPublishSettings(userDetails.getUserId(), workspaceId));
    }


    @PutMapping
    public ResponseEntity<PublishSettingResponse> updatePublishSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @RequestBody WorkspacePublishRequest request) {
        return ResponseEntity.ok(
                publishService.updatePublishSettings(userDetails.getUserId(), workspaceId, request));
    }
}