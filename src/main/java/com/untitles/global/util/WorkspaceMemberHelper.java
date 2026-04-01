package com.untitles.global.util;

import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceMemberHelper {

    private final WorkspaceMemberRepository workspaceMemberRepository;

    /**
     * 워크스페이스 멤버 조회 - 멤버가 아니면 예외
     */
    public WorkspaceMember getMemberOrThrow(Long userId, Long workspaceId) {
        return workspaceMemberRepository.findByWorkspaceWorkspaceIdAndUserUserId(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
    }

    /**
     * 쓰기 권한 확인 - VIEWER는 불가
     */
    public void checkWritePermission(WorkspaceMember member) {
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException(ErrorCode.WRITE_PERMISSION_DENIED);
        }
    }

    /**
     * 권한 레벨 확인 - OWNER, ADMIN 등 특정 권한 이상 필요
     */
    public void checkPermission(WorkspaceMember member, WorkspaceRole requiredRole) {
        if (!member.getRole().hasPermission(requiredRole)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
    }
}
