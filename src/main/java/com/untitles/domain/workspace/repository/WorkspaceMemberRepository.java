package com.untitles.domain.workspace.repository;

import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    
    Optional<WorkspaceMember> findByWorkspaceAndUser(Workspace workspace, Users user);
    
    List<WorkspaceMember> findAllByWorkspace(Workspace workspace);
    
    List<WorkspaceMember> findAllByUser(Users user);
    
    boolean existsByWorkspaceAndUser(Workspace workspace, Users user);
    
    // 특정 워크스페이스의 OWNER 찾기
    Optional<WorkspaceMember> findByWorkspaceAndRole(Workspace workspace, WorkspaceRole role);
}