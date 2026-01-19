package com.untitles.domain.folder.controller;

import com.untitles.domain.folder.dto.request.FolderCreateRequestDTO;
import com.untitles.domain.folder.dto.request.FolderUpdateRequestDTO;
import com.untitles.domain.folder.dto.request.MoveFolderRequestDTO;
import com.untitles.domain.folder.dto.response.FolderResponseDTO;
import com.untitles.domain.folder.service.FolderService;
import com.untitles.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/folders")
public class FolderController {

    private final FolderService folderService;

    /**
     * 루트 폴더 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<FolderResponseDTO>> getRootFolders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(folderService.getRootFolders(userDetails.getUserId(), workspaceId));
    }

    /**
     * 하위 폴더 목록 조회
     */
    @GetMapping("/{folderId}/children")
    public ResponseEntity<List<FolderResponseDTO>> getChildFolders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long folderId) {
        return ResponseEntity.ok(folderService.getChildFolders(userDetails.getUserId(), workspaceId, folderId));
    }

    /**
     * 폴더 생성
     */
    @PostMapping
    public ResponseEntity<FolderResponseDTO> createFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @RequestBody @Valid FolderCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(folderService.createFolder(userDetails.getUserId(), workspaceId, request));
    }

    /**
     * 폴더 수정
     */
    @PutMapping("/{folderId}")
    public ResponseEntity<FolderResponseDTO> updateFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long folderId,
            @RequestBody @Valid FolderUpdateRequestDTO request) {
        return ResponseEntity.ok(folderService.updateFolder(userDetails.getUserId(), workspaceId, folderId, request));
    }

    /**
     * 폴더 삭제
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long folderId) {
        folderService.deleteFolder(userDetails.getUserId(), workspaceId, folderId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 폴더 이동
     */
    @PutMapping("/{folderId}/move")
    public ResponseEntity<FolderResponseDTO> moveFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long folderId,
            @RequestBody MoveFolderRequestDTO request) {
        return ResponseEntity.ok(folderService.moveFolder(
                userDetails.getUserId(), workspaceId, folderId, request.getParentId()));
    }
}