package com.untitles.domain.workspace.service;

import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.dto.request.MemberInviteRequest;
import com.untitles.domain.workspace.dto.request.MemberRoleUpdateRequest;
import com.untitles.domain.workspace.dto.request.WorkspaceCreateRequest;
import com.untitles.domain.workspace.dto.request.WorkspaceUpdateRequest;
import com.untitles.domain.workspace.dto.response.WorkspaceMemberResponse;
import com.untitles.domain.workspace.dto.response.WorkspaceResponse;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    // 워크스페이스 생성
    @Transactional
    public WorkspaceResponse createWorkspace(Long userId, WorkspaceCreateRequest request) {
        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Workspace workspace = Workspace.builder()
            .name(request.name())
            .description(request.description())
            .build();

        workspaceRepository.save(workspace);

        // 생성자를 OWNER로 추가
        WorkspaceMember owner = WorkspaceMember.builder()
            .workspace(workspace)
            .user(user)
            .role(WorkspaceRole.OWNER)
            .build();

        memberRepository.save(owner);

        return WorkspaceResponse.from(workspace, WorkspaceRole.OWNER);
    }

    // 내 워크스페이스 목록
    public List<WorkspaceResponse> getMyWorkspaces(Long userId) {
        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return memberRepository.findAllByUser(user).stream()
            .map(member -> WorkspaceResponse.from(member.getWorkspace(), member.getRole()))
            .toList();
    }

    // 워크스페이스 상세
    public WorkspaceResponse getWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        return WorkspaceResponse.from(member.getWorkspace(), member.getRole());
    }

    // 워크스페이스 수정 (OWNER, ADMIN만)
    @Transactional
    public WorkspaceResponse updateWorkspace(Long userId, Long workspaceId, WorkspaceUpdateRequest request) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkPermission(member, WorkspaceRole.ADMIN);

        Workspace workspace = member.getWorkspace();
        if (request.name() != null) workspace.updateName(request.name());
        if (request.description() != null) workspace.updateDescription(request.description());

        return WorkspaceResponse.from(workspace, member.getRole());
    }

    // 워크스페이스 삭제 (OWNER만)
    @Transactional
    public void deleteWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkPermission(member, WorkspaceRole.OWNER);

        workspaceRepository.delete(member.getWorkspace());
    }

    // 멤버 초대 (OWNER, ADMIN만)
    @Transactional
    public WorkspaceMemberResponse inviteMember(Long userId, Long workspaceId, MemberInviteRequest request) {
        WorkspaceMember inviter = getMemberOrThrow(userId, workspaceId);
        checkPermission(inviter, WorkspaceRole.ADMIN);

        // OWNER 권한은 부여 불가
        if (request.role() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("OWNER 권한은 부여할 수 없습니다.");
        }

        Users invitee = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new IllegalArgumentException("해당 이메일의 사용자를 찾을 수 없습니다."));

        // 이미 멤버인지 확인
        if (memberRepository.existsByWorkspaceAndUser(inviter.getWorkspace(), invitee)) {
            throw new IllegalArgumentException("이미 워크스페이스 멤버입니다.");
        }

        WorkspaceMember newMember = WorkspaceMember.builder()
            .workspace(inviter.getWorkspace())
            .user(invitee)
            .role(request.role())
            .build();

        memberRepository.save(newMember);
        return WorkspaceMemberResponse.from(newMember);
    }

    // 멤버 목록 조회
    public List<WorkspaceMemberResponse> getMembers(Long userId, Long workspaceId) {
        getMemberOrThrow(userId, workspaceId);  // 권한 확인

        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));

        return memberRepository.findAllByWorkspace(workspace).stream()
            .map(WorkspaceMemberResponse::from)
            .toList();
    }

    // 멤버 권한 변경 (OWNER, ADMIN만)
    @Transactional
    public WorkspaceMemberResponse updateMemberRole(Long userId, Long workspaceId,
                                                    Long targetMemberId, MemberRoleUpdateRequest request) {
        WorkspaceMember requester = getMemberOrThrow(userId, workspaceId);
        checkPermission(requester, WorkspaceRole.ADMIN);

        WorkspaceMember target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));

        if (!target.getWorkspace().getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("해당 워크스페이스의 멤버가 아닙니다.");
        }
        // OWNER 권한 변경 불가
        if (target.getRole() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("OWNER의 권한은 변경할 수 없습니다.");
        }
        if (request.role() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("OWNER 권한은 부여할 수 없습니다.");
        }

        target.updateRole(request.role());
        return WorkspaceMemberResponse.from(target);
    }

    // 멤버 제거 (OWNER, ADMIN만)
    @Transactional
    public void removeMember(Long userId, Long workspaceId, Long targetMemberId) {
        WorkspaceMember requester = getMemberOrThrow(userId, workspaceId);
        checkPermission(requester, WorkspaceRole.ADMIN);

        WorkspaceMember target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));

        if (!target.getWorkspace().getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("해당 워크스페이스의 멤버가 아닙니다.");
        }
        if (target.getRole() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("OWNER는 제거할 수 없습니다.");
        }

        memberRepository.delete(target);
    }

    // 워크스페이스 나가기
    @Transactional
    public void leaveWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);

        if (member.getRole() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("OWNER는 워크스페이스를 나갈 수 없습니다. 삭제하거나 양도하세요.");
        }

        memberRepository.delete(member);
    }

    // 헬퍼 메서드
    private WorkspaceMember getMemberOrThrow(Long userId, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return memberRepository.findByWorkspaceAndUser(workspace, user)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스 접근 권한이 없습니다."));
    }

    private void checkPermission(WorkspaceMember member, WorkspaceRole requiredRole) {
        if (member.getRole().ordinal() > requiredRole.ordinal()) {
            throw new IllegalArgumentException("권한이 부족합니다.");
        }
    }
}