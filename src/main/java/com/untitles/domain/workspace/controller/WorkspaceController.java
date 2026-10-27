package com.untitles.domain.workspace.controller;

import com.untitles.domain.workspace.dto.request.MemberInviteRequest;
import com.untitles.domain.workspace.dto.request.MemberRoleUpdateRequest;
import com.untitles.domain.workspace.dto.request.WorkspaceCreateRequest;
import com.untitles.domain.workspace.dto.request.WorkspaceUpdateRequest;
import com.untitles.domain.workspace.dto.response.WorkspaceMemberResponse;
import com.untitles.domain.workspace.dto.response.WorkspaceResponse;
import com.untitles.domain.workspace.service.WorkspaceService;
import com.untitles.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final com.untitles.domain.invitation.service.InvitationService invitationService;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid WorkspaceCreateRequest request) {
        return ResponseEntity.ok(workspaceService.createWorkspace(userDetails.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> getMyWorkspaces(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(workspaceService.getMyWorkspaces(userDetails.getUserId()));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(workspaceService.getWorkspace(userDetails.getUserId(), workspaceId));
    }

    @PutMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @RequestBody @Valid WorkspaceUpdateRequest request) {
        return ResponseEntity.ok(workspaceService.updateWorkspace(userDetails.getUserId(), workspaceId, request));
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        workspaceService.deleteWorkspace(userDetails.getUserId(), workspaceId);
        return ResponseEntity.noContent().build();
    }

    // 멤버 관리
    /**
     * 예전 앱 버전 호환: 바로 멤버로 추가하던 API. 이제는 초대를 보내고, 상대가 수락해야 멤버가 된다.
     * 새 버전은 POST /workspaces/{id}/invitations 를 쓴다.
     */
    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<WorkspaceMemberResponse> inviteMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @RequestBody @Valid MemberInviteRequest request) {
        var invitation = invitationService.invite(userDetails.getUserId(), workspaceId,
                new com.untitles.domain.invitation.dto.InvitationCreateRequest(request.userId(), request.email(), request.role()));
        return ResponseEntity.ok(WorkspaceMemberResponse.builder()
                .userId(invitation.inviteeUserId())
                .nickname(invitation.inviteeNickname())
                .email(invitation.email())
                .role(invitation.role())
                .build());
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<WorkspaceMemberResponse>> getMembers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(workspaceService.getMembers(userDetails.getUserId(), workspaceId));
    }

    @PutMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<WorkspaceMemberResponse> updateMemberRole(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @RequestBody @Valid MemberRoleUpdateRequest request) {
        return ResponseEntity.ok(workspaceService.updateMemberRole(
                userDetails.getUserId(), workspaceId, memberId, request));
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @PathVariable Long memberId) {
        workspaceService.removeMember(userDetails.getUserId(), workspaceId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{workspaceId}/leave")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        workspaceService.leaveWorkspace(userDetails.getUserId(), workspaceId);
        return ResponseEntity.noContent().build();
    }

    // 워크스페이스 생성 제한 정보
    @GetMapping("/limit")
    public ResponseEntity<Map<String, Integer>> getWorkspaceLimit(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        int count = workspaceService.getTeamWorkspaceCount(userDetails.getUserId());

        return ResponseEntity.ok(Map.of(
                "count", count,
                "limit", 3
        ));
    }
}