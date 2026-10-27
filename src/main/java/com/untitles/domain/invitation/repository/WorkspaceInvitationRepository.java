package com.untitles.domain.invitation.repository;

import com.untitles.domain.invitation.entity.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, Long> {

    Optional<WorkspaceInvitation> findByToken(String token);

    Optional<WorkspaceInvitation> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("SELECT i FROM WorkspaceInvitation i WHERE i.workspaceId = :workspaceId AND i.expiresAt > :now ORDER BY i.createdAt DESC")
    List<WorkspaceInvitation> findPendingByWorkspace(@Param("workspaceId") Long workspaceId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(i) FROM WorkspaceInvitation i WHERE i.workspaceId = :workspaceId AND i.expiresAt > :now")
    long countPendingByWorkspace(@Param("workspaceId") Long workspaceId, @Param("now") LocalDateTime now);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndInviteeUserId(Long workspaceId, Long inviteeUserId);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmail(Long workspaceId, String email);

    /** 나에게 온 대기 중인 초대: 내 계정으로 온 것 + (확인된) 내 이메일로 온 것 */
    @Query("SELECT i FROM WorkspaceInvitation i WHERE i.expiresAt > :now AND "
            + "(i.inviteeUserId = :userId OR (i.inviteeUserId IS NULL AND :email IS NOT NULL AND i.email = :email)) "
            + "ORDER BY i.createdAt DESC")
    List<WorkspaceInvitation> findPendingForUser(@Param("userId") Long userId, @Param("email") String email,
                                                 @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WorkspaceInvitation i WHERE i.workspaceId = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WorkspaceInvitation i WHERE i.inviteeUserId = :userId OR i.inviterId = :userId")
    void deleteAllByUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM WorkspaceInvitation i WHERE i.expiresAt < :before")
    int deleteExpired(@Param("before") LocalDateTime before);
}
