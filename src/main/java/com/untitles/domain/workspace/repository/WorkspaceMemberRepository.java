package com.untitles.domain.workspace.repository;

import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.entity.WorkspaceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceAndUser(Workspace workspace, Users user);

    List<WorkspaceMember> findAllByWorkspace(Workspace workspace);

    List<WorkspaceMember> findAllByUser(Users user);

    boolean existsByWorkspaceAndUser(Workspace workspace, Users user);

    @Query("SELECT m FROM WorkspaceMember m " +
            "JOIN FETCH m.workspace w " +
            "JOIN FETCH w.members " +
            "WHERE m.user = :user"
    )
    List<WorkspaceMember> findAllByUserWithWorkspaceAndMembers(@Param("user") Users user);

    //TEAM 타입만 카운트
    long countByUserAndRoleAndWorkspaceType(Users user, WorkspaceRole role, WorkspaceType type);


    long countByWorkspace(Workspace workspace);
}