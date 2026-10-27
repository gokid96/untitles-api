package com.untitles.domain.invitation.controller;

import com.untitles.domain.invitation.dto.InvitationCreateRequest;
import com.untitles.domain.invitation.dto.InvitationPreview;
import com.untitles.domain.invitation.dto.InvitationResponse;
import com.untitles.domain.invitation.service.InvitationService;
import com.untitles.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 워크스페이스 초대
 * - 관리자: POST/GET /workspaces/{id}/invitations, DELETE /workspaces/{id}/invitations/{invitationId}
 * - 초대받은 사람: GET /invitations (내 초대), POST /invitations/{token}/accept|decline
 * - 초대 링크 미리보기(로그인 전): GET /public/invitations/{token}
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping("/workspaces/{workspaceId}/invitations")
    public ResponseEntity<InvitationResponse> invite(@AuthenticationPrincipal CustomUserDetails user,
                                                     @PathVariable Long workspaceId,
                                                     @RequestBody @Valid InvitationCreateRequest request) {
        return ResponseEntity.ok(invitationService.invite(user.getUserId(), workspaceId, request));
    }

    @GetMapping("/workspaces/{workspaceId}/invitations")
    public ResponseEntity<List<InvitationResponse>> pending(@AuthenticationPrincipal CustomUserDetails user,
                                                            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(invitationService.pendingForWorkspace(user.getUserId(), workspaceId));
    }

    @DeleteMapping("/workspaces/{workspaceId}/invitations/{invitationId}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal CustomUserDetails user,
                                       @PathVariable Long workspaceId, @PathVariable Long invitationId) {
        invitationService.cancel(user.getUserId(), workspaceId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invitations")
    public ResponseEntity<List<InvitationResponse>> mine(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(invitationService.mine(user.getUserId()));
    }

    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<Map<String, Long>> accept(@AuthenticationPrincipal CustomUserDetails user,
                                                    @PathVariable String token) {
        return ResponseEntity.ok(Map.of("workspaceId", invitationService.accept(user.getUserId(), token)));
    }

    @PostMapping("/invitations/{token}/decline")
    public ResponseEntity<Void> decline(@AuthenticationPrincipal CustomUserDetails user, @PathVariable String token) {
        invitationService.decline(user.getUserId(), token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/invitations/{token}")
    public ResponseEntity<InvitationPreview> preview(@PathVariable String token) {
        return ResponseEntity.ok(invitationService.preview(token));
    }
}
