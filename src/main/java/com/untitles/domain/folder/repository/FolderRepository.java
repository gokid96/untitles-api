package com.untitles.domain.folder.repository;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    // 워크스페이스에 속한 폴더를 ID로 조회(권한 체크)
    Optional<Folder> findByFolderIdAndWorkspaceWorkspaceId(Long folderId, Long workspaceId);

    List<Folder> findAllByWorkspaceWorkspaceId(Long workspaceId);

    @Modifying
    @Query("UPDATE Folder f SET f.parent = null WHERE f.workspace.workspaceId = :workspaceId")
    void clearParentByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Modifying
    @Query("DELETE FROM Folder f WHERE f.workspace.workspaceId = :workspaceId")
            void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);

        // 워크스페이스의 루트 폴더들 조회
    List<Folder> findByWorkspaceWorkspaceIdAndParentIsNull(Long workspaceId);

    long countByWorkspace(Workspace workspace);

    // 회원 탈퇴 시 다른 워크스페이스에 만든 폴더의 작성자 정보를 비운다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Folder f SET f.user = null WHERE f.user.userId = :userId")
    void clearUser(@Param("userId") Long userId);
}
