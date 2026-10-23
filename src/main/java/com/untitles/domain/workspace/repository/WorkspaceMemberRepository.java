package com.untitles.domain.workspace.repository;

import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.entity.WorkspaceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    List<WorkspaceMember> findAllByWorkspace(Workspace workspace);

    boolean existsByWorkspaceAndUser(Workspace workspace, Users user);

    @Query("SELECT m FROM WorkspaceMember m " +
            "JOIN FETCH m.workspace w " +
            "JOIN FETCH w.members " +
            "WHERE m.user = :user"
    )
    List<WorkspaceMember> findAllByUserWithWorkspaceAndMembers(@Param("user") Users user);

    //TEAM 타입만 카운트
    long countByUserAndRoleAndWorkspaceType(Users user, WorkspaceRole role, WorkspaceType type);

    Optional<WorkspaceMember> findByWorkspaceWorkspaceIdAndUserUserId(Long workspaceId, Long userId);

    @Query("SELECT m FROM WorkspaceMember m JOIN FETCH m.workspace WHERE m.workspace.workspaceId = :workspaceId AND m.user.userId = :userId")
    Optional<WorkspaceMember> findWithWorkspaceByWorkspaceIdAndUserId(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    //워크스페이스 삭제시 벌크연산
    @Modifying
    @Query("DELETE FROM WorkspaceMember wm WHERE wm.workspace.workspaceId = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);


    long countByWorkspace(Workspace workspace);

    List<WorkspaceMember> findAllByUserUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WorkspaceMember wm WHERE wm.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

}