package com.untitles.domain.post.controller;

import com.untitles.domain.post.dto.request.MovePostRequestDTO;
import com.untitles.domain.post.dto.request.PostCreateRequestDTO;
import com.untitles.domain.post.dto.request.PostUpdateRequestDTO;
import com.untitles.domain.post.dto.response.PostResponseDTO;
import com.untitles.domain.post.service.PostService;
import com.untitles.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<List<PostResponseDTO>> getPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(postService.getPosts(userDetails.getUserId(), workspaceId));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponseDTO> getPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPost(userDetails.getUserId(), workspaceId, postId));
    }

    @PostMapping
    public ResponseEntity<PostResponseDTO> createPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @RequestBody @Valid PostCreateRequestDTO request) {
        return ResponseEntity.ok(postService.createPost(userDetails.getUserId(), workspaceId, request));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<PostResponseDTO> updatePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long postId,
            @RequestBody @Valid PostUpdateRequestDTO request) {
        return ResponseEntity.ok(postService.updatePost(userDetails.getUserId(), workspaceId, postId, request));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long postId) {
        postService.deletePost(userDetails.getUserId(), workspaceId, postId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{postId}/move")
    public ResponseEntity<PostResponseDTO> movePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long postId,
            @RequestBody MovePostRequestDTO request) {
        return ResponseEntity.ok(postService.movePost(
                userDetails.getUserId(), workspaceId, postId, request.getFolderId()));
    }
}