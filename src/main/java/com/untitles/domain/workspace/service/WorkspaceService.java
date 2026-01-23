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
import com.untitles.domain.workspace.entity.WorkspaceType;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    // 워크스페이스 생성
    @Transactional
    public WorkspaceResponse createWorkspace(Long userId, WorkspaceCreateRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        long workspaceCount = workspaceMemberRepository.countByUserAndRoleAndWorkspaceType(
                user, WorkspaceRole.OWNER, WorkspaceType.TEAM
        );
        if (workspaceCount >= 3) {
            throw new IllegalArgumentException("워크스페이스는 최대 3개까지 생성할 수 있습니다.");
        }
        Workspace workspace = Workspace.builder()
                .name(request.name())
                .description(request.description())
                .type(WorkspaceType.TEAM)
                .build();

        workspaceRepository.save(workspace);

        // 생성자를 OWNER로 추가
        WorkspaceMember owner = WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build();

        workspaceMemberRepository.save(owner);

        return WorkspaceResponse.from(workspace, WorkspaceRole.OWNER);
    }

    // 내 워크스페이스 목록
    public List<WorkspaceResponse> getMyWorkspaces(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return workspaceMemberRepository.findAllByUserWithWorkspaceAndMembers(user).stream()
                .map(member -> WorkspaceResponse.from(
                        member.getWorkspace(),  // 이미 조회됨, 추가 쿼리 없음
                        member.getRole()
                ))
                .toList();
    }

    // 워크스페이스 상세
    public WorkspaceResponse getWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        return WorkspaceResponse.from(member.getWorkspace(), member.getRole());
    }

    // 워크스페이스 수정 (OWNER, ADMIN만)
    public WorkspaceResponse updateWorkspace(Long userId, Long workspaceId, WorkspaceUpdateRequest request) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkPermission(member, WorkspaceRole.ADMIN);

        Workspace workspace = member.getWorkspace();
        if (request.name() != null) workspace.updateName(request.name());
        if (request.description() != null) workspace.updateDescription(request.description());

        workspaceRepository.save(workspace);
        return WorkspaceResponse.from(workspace, member.getRole());
    }

    // 워크스페이스 삭제 (OWNER만)
    public void deleteWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkPermission(member, WorkspaceRole.OWNER);

        Workspace workspace = member.getWorkspace();
        if (workspace.getType() == WorkspaceType.PERSONAL) {
            throw new IllegalArgumentException("개인 워크스페이스는 삭제할 수 없습니다.");
        }


        workspaceRepository.delete(member.getWorkspace());
    }

    // 멤버 초대 (OWNER, ADMIN만)
    public WorkspaceMemberResponse inviteMember(Long userId, Long workspaceId, MemberInviteRequest request) {
        WorkspaceMember inviter = getMemberOrThrow(userId, workspaceId);
        checkPermission(inviter, WorkspaceRole.ADMIN);

        // OWNER 권한은 부여 불가
        if (request.role() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("OWNER 권한은 부여할 수 없습니다.");
        }

        Users invitee = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일의 사용자를 찾을 수 없습니다."));

        // inviteMember() - 개인 워크스페이스 초대방지
        long memberCount = workspaceMemberRepository.countByWorkspace(inviter.getWorkspace());
        if(memberCount >= 5){
            throw new IllegalArgumentException("워크스페이스 멤버는 최대 5명 까지 초대할 수 있습니다.");
        }

        if (inviter.getWorkspace().getType() == WorkspaceType.PERSONAL) {
            throw new IllegalArgumentException("개인 워크스페이스에는 멤버를 초대할 수 없습니다.");
        }

        // 이미 멤버인지 확인
        if (workspaceMemberRepository.existsByWorkspaceAndUser(inviter.getWorkspace(), invitee)) {
            throw new IllegalArgumentException("이미 워크스페이스 멤버입니다.");
        }

        WorkspaceMember newMember = WorkspaceMember.builder()
                .workspace(inviter.getWorkspace())
                .user(invitee)
                .role(request.role())
                .build();

        workspaceMemberRepository.save(newMember);
        return WorkspaceMemberResponse.from(newMember);
    }

    // 멤버 목록 조회
    public List<WorkspaceMemberResponse> getMembers(Long userId, Long workspaceId) {
        getMemberOrThrow(userId, workspaceId);  // 권한 확인

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));

        return workspaceMemberRepository.findAllByWorkspace(workspace).stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    // 멤버 권한 변경 (OWNER, ADMIN만)
    public WorkspaceMemberResponse updateMemberRole(Long userId, Long workspaceId,
                                                    Long targetMemberId, MemberRoleUpdateRequest request) {
        WorkspaceMember requester = getMemberOrThrow(userId, workspaceId);
        checkPermission(requester, WorkspaceRole.ADMIN);

        WorkspaceMember target = workspaceMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));

        if (!target.getWorkspace().getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("해당 워크스페이스의 멤버가 아닙니다.");
        }
        // 자기보다 높은 숫자만 변경가능
        if (requester.getRole() != WorkspaceRole.OWNER
                && target.getRole().getLevel() <= requester.getRole().getLevel()) {
            throw new IllegalArgumentException("동급 이상의 권한은 변경할 수 없습니다.");
        }
        if (request.role() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("OWNER 권한은 부여할 수 없습니다.");
        }

        target.updateRole(request.role());
        workspaceMemberRepository.save(target);
        return WorkspaceMemberResponse.from(target);
    }

    // 멤버 제거 (OWNER, ADMIN만)
    public void removeMember(Long userId, Long workspaceId, Long targetMemberId) {
        WorkspaceMember requester = getMemberOrThrow(userId, workspaceId);
        checkPermission(requester, WorkspaceRole.ADMIN);

        WorkspaceMember target = workspaceMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));

        if (!target.getWorkspace().getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("해당 워크스페이스의 멤버가 아닙니다.");
        }
        // 자기보다 높은 숫자만 변경가능
        if (requester.getRole() != WorkspaceRole.OWNER
                && target.getRole().getLevel() <= requester.getRole().getLevel()) {
            throw new IllegalArgumentException("동급 이상의 권한은 삭제할 수 없습니다.");
        }

        workspaceMemberRepository.delete(target);
    }

    // 워크스페이스 나가기
    public void leaveWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);

        if (member.getRole() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("OWNER는 워크스페이스를 나갈 수 없습니다. 삭제하거나 양도하세요.");
        }

        workspaceMemberRepository.delete(member);
    }

    // 헬퍼 메서드
    private WorkspaceMember getMemberOrThrow(Long userId, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스 접근 권한이 없습니다."));
    }

    private void checkPermission(WorkspaceMember member, WorkspaceRole requiredRole) {
        if (!member.getRole().hasPermission(requiredRole)) {
            throw new IllegalArgumentException("권한이 부족합니다.");
        }
    }
}