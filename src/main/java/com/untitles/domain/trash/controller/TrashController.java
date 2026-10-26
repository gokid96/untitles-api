package com.untitles.domain.trash.controller;

import com.untitles.domain.trash.dto.TrashResponse;
import com.untitles.domain.trash.service.TrashService;
import com.untitles.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    @GetMapping
    public ResponseEntity<TrashResponse> getTrash(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(trashService.getTrash(userDetails.getUserId(), workspaceId));
    }

    @PostMapping("/posts/{postId}/restore")
    public ResponseEntity<Void> restorePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId, @PathVariable Long postId) {
        trashService.restorePost(userDetails.getUserId(), workspaceId, postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/folders/{folderId}/restore")
    public ResponseEntity<Void> restoreFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId, @PathVariable Long folderId) {
        trashService.restoreFolder(userDetails.getUserId(), workspaceId, folderId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId, @PathVariable Long postId) {
        trashService.deletePostPermanently(userDetails.getUserId(), workspaceId, postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId, @PathVariable Long folderId) {
        trashService.deleteFolderPermanently(userDetails.getUserId(), workspaceId, folderId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> emptyTrash(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        trashService.emptyTrash(userDetails.getUserId(), workspaceId);
        return ResponseEntity.noContent().build();
    }
}
