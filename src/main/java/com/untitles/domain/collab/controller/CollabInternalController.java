package com.untitles.domain.collab.controller;

import com.untitles.domain.collab.dto.CollabDtos.AccessResponse;
import com.untitles.domain.collab.dto.CollabDtos.DocumentResponse;
import com.untitles.domain.collab.dto.CollabDtos.StoreRequest;
import com.untitles.domain.collab.dto.CollabDtos.StoreResponse;
import com.untitles.domain.collab.service.CollabService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 실시간 편집 서버(Hocuspocus)만 쓰는 내부 API.
 * 공개 프록시(Caddy)에서 /internal 경로를 막고, 여기서도 공유 비밀값(COLLAB_SECRET)을 확인한다.
 * 비밀값이 설정되지 않았으면 기능 자체가 꺼진다(404).
 */
@RestController
@RequestMapping("/internal/collab")
@RequiredArgsConstructor
public class CollabInternalController {

    private final CollabService collabService;

    @Value("${app.collab.secret:}")
    private String secret;

    @GetMapping("/access")
    public ResponseEntity<AccessResponse> access(@RequestHeader(value = "X-Collab-Secret", required = false) String header,
                                                 @RequestParam Long userId,
                                                 @RequestParam Long workspaceId,
                                                 @RequestParam Long postId) {
        if (!authorized(header)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(collabService.access(userId, workspaceId, postId));
    }

    @GetMapping("/workspaces/{workspaceId}/posts/{postId}")
    public ResponseEntity<DocumentResponse> load(@RequestHeader(value = "X-Collab-Secret", required = false) String header,
                                                 @PathVariable Long workspaceId,
                                                 @PathVariable Long postId) {
        if (!authorized(header)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(collabService.load(workspaceId, postId));
    }

    @PutMapping("/workspaces/{workspaceId}/posts/{postId}")
    public ResponseEntity<StoreResponse> store(@RequestHeader(value = "X-Collab-Secret", required = false) String header,
                                               @PathVariable Long workspaceId,
                                               @PathVariable Long postId,
                                               @RequestBody StoreRequest request) {
        if (!authorized(header)) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(collabService.store(workspaceId, postId,
                    request.title(), request.content(), request.ydoc(), request.baseHash()));
        } catch (CollabService.StaleBaseException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getCurrent());
        }
    }

    private boolean authorized(String header) {
        if (secret == null || secret.isBlank() || header == null) return false;
        return MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }
}
