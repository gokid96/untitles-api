package com.untitles.domain.workspace.service;

import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.repository.PostRepository;
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
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.WorkspaceMemberHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FolderRepository folderRepository;
    private final WorkspaceMemberHelper workspaceMemberHelper;

    // 워크스페이스 생성
    @Transactional
    public WorkspaceResponse createWorkspace(Long userId, WorkspaceCreateRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long workspaceCount = workspaceMemberRepository.countByUserAndRoleAndWorkspaceType(
                user, WorkspaceRole.OWNER, WorkspaceType.TEAM
        );
        if (workspaceCount >= 3) {
            throw new BusinessException(ErrorCode.WORKSPACE_LIMIT_EXCEEDED);
        }

        Workspace workspace = Workspace.createTeam(request.name(), request.description());
        workspaceRepository.save(workspace);

        WorkspaceMember owner = WorkspaceMember.createOwner(workspace, user);
        workspaceMemberRepository.save(owner);

        return WorkspaceResponse.from(workspace, WorkspaceRole.OWNER);
    }

    // 내 워크스페이스 목록
    public List<WorkspaceResponse> getMyWorkspaces(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return workspaceMemberRepository.findAllByUserWithWorkspaceAndMembers(user).stream()
                .map(member -> WorkspaceResponse.from(
                        member.getWorkspace(),
                        member.getRole()
                ))
                .toList();
    }

    // 워크스페이스 상세
    public WorkspaceResponse getWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        return WorkspaceResponse.from(member.getWorkspace(), member.getRole());
    }

    // 워크스페이스 수정 (OWNER, ADMIN만)
    @Transactional
    public WorkspaceResponse updateWorkspace(Long userId, Long workspaceId, WorkspaceUpdateRequest request) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkPermission(member, WorkspaceRole.ADMIN);

        Workspace workspace = member.getWorkspace();
        if (request.name() != null) workspace.updateName(request.name());
        if (request.description() != null) workspace.updateDescription(request.description());

        workspaceRepository.save(workspace);
        return WorkspaceResponse.from(workspace, member.getRole());
    }

    // 워크스페이스 삭제 (OWNER만)
    @Transactional
    public void deleteWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkPermission(member, WorkspaceRole.OWNER);

        Workspace workspace = member.getWorkspace();
        if (workspace.getType() == WorkspaceType.PERSONAL) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_PERSONAL_WORKSPACE);
        }
        /**
         * 삭제 순서 중요! FK 제약 조건 때문에 순서 바꾸면 에러남
         * post → folder(parent null) → folder → member → workspace
         */
        postRepository.deleteByWorkspaceWorkspaceId(workspaceId);
        folderRepository.clearParentByWorkspaceId(workspaceId);
        folderRepository.deleteAllByWorkspaceId(workspaceId);
        workspaceMemberRepository.deleteAllByWorkspaceId(workspaceId);
        workspaceRepository.delete(member.getWorkspace());
    }

    // 멤버 초대 (OWNER, ADMIN만)
    @Transactional
    public WorkspaceMemberResponse inviteMember(Long userId, Long workspaceId, MemberInviteRequest request) {
        WorkspaceMember inviter = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkPermission(inviter, WorkspaceRole.ADMIN);

        if (request.role() == WorkspaceRole.OWNER) {
            throw new BusinessException(ErrorCode.CANNOT_ASSIGN_OWNER_ROLE);
        }

        Users invitee = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long memberCount = workspaceMemberRepository.countByWorkspace(inviter.getWorkspace());
        if (memberCount >= 5) {
            throw new BusinessException(ErrorCode.MEMBER_LIMIT_EXCEEDED);
        }

        if (inviter.getWorkspace().getType() == WorkspaceType.PERSONAL) {
            throw new BusinessException(ErrorCode.CANNOT_INVITE_TO_PERSONAL_WORKSPACE);
        }

        if (workspaceMemberRepository.existsByWorkspaceAndUser(inviter.getWorkspace(), invitee)) {
            throw new BusinessException(ErrorCode.ALREADY_WORKSPACE_MEMBER);
        }

        WorkspaceMember newMember = WorkspaceMember.createMember(inviter.getWorkspace(), invitee, request.role());
        workspaceMemberRepository.save(newMember);
        return WorkspaceMemberResponse.from(newMember);
    }

    // 멤버 목록 조회
    public List<WorkspaceMemberResponse> getMembers(Long userId, Long workspaceId) {
        workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        return workspaceMemberRepository.findAllByWorkspace(workspace).stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    // 멤버 권한 변경 (OWNER, ADMIN만)
    @Transactional
    public WorkspaceMemberResponse updateMemberRole(Long userId, Long workspaceId,
                                                    Long targetMemberId, MemberRoleUpdateRequest request) {
        WorkspaceMember requester = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkPermission(requester, WorkspaceRole.ADMIN);

        WorkspaceMember target = workspaceMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!target.getWorkspace().getWorkspaceId().equals(workspaceId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (requester.getRole() != WorkspaceRole.OWNER
                && target.getRole().getLevel() <= requester.getRole().getLevel()) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_HIGHER_ROLE);
        }
        if (request.role() == WorkspaceRole.OWNER) {
            throw new BusinessException(ErrorCode.CANNOT_ASSIGN_OWNER_ROLE);
        }

        target.updateRole(request.role());
        workspaceMemberRepository.save(target);
        return WorkspaceMemberResponse.from(target);
    }

    // 멤버 제거 (OWNER, ADMIN만)
    @Transactional
    public void removeMember(Long userId, Long workspaceId, Long targetMemberId) {
        WorkspaceMember requester = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkPermission(requester, WorkspaceRole.ADMIN);

        WorkspaceMember target = workspaceMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!target.getWorkspace().getWorkspaceId().equals(workspaceId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (requester.getRole() != WorkspaceRole.OWNER
                && target.getRole().getLevel() <= requester.getRole().getLevel()) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_HIGHER_ROLE);
        }

        workspaceMemberRepository.delete(target);
    }

    // 워크스페이스 나가기
    @Transactional
    public void leaveWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);

        if (member.getRole() == WorkspaceRole.OWNER) {
            throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
        }

        workspaceMemberRepository.delete(member);
    }

    public int getTeamWorkspaceCount(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return (int) workspaceMemberRepository.countByUserAndRoleAndWorkspaceType(
                user, WorkspaceRole.OWNER, WorkspaceType.TEAM
        );
    }
}
