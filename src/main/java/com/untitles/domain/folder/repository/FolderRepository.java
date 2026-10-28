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

    // 워크스페이스 삭제 시 벌크 연산 (휴지통에 있는 폴더까지 모두 삭제하려고 네이티브 쿼리 사용)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE folder SET parent_id = NULL WHERE workspace_id = :workspaceId", nativeQuery = true)
    void clearParentByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM folder WHERE workspace_id = :workspaceId", nativeQuery = true)
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);

    // ===== 휴지통 (삭제된 폴더는 엔티티 조회에서 제외되므로 네이티브 쿼리로 다룬다) =====

    interface FolderRow {
        Long getFolderId();
        Long getParentId();
        String getName();
        java.time.LocalDateTime getDeletedAt();
    }

    // 휴지통 포함 워크스페이스의 모든 폴더 (하위 폴더 계산용)
    @Query(value = "SELECT folder_id AS folderId, parent_id AS parentId, name, deleted_at AS deletedAt "
            + "FROM folder WHERE workspace_id = :workspaceId", nativeQuery = true)
    List<FolderRow> findAllRowsByWorkspace(@Param("workspaceId") Long workspaceId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE folder SET deleted_at = :deletedAt WHERE folder_id IN (:folderIds) AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteByIds(@Param("folderIds") java.util.Collection<Long> folderIds, @Param("deletedAt") java.time.LocalDateTime deletedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE folder SET deleted_at = NULL WHERE folder_id IN (:folderIds)", nativeQuery = true)
    int restoreByIds(@Param("folderIds") java.util.Collection<Long> folderIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE folder SET parent_id = NULL WHERE folder_id = :folderId", nativeQuery = true)
    int moveToRoot(@Param("folderId") Long folderId);

    @Query(value = "SELECT folder_id AS folderId, parent_id AS parentId, name, deleted_at AS deletedAt "
            + "FROM folder WHERE deleted_at IS NOT NULL AND deleted_at < :before", nativeQuery = true)
    List<FolderRow> findRowsTrashedBefore(@Param("before") java.time.LocalDateTime before);

    @Query(value = "SELECT DISTINCT workspace_id FROM folder WHERE folder_id IN (:folderIds)", nativeQuery = true)
    List<Long> findWorkspaceIdsByFolderIds(@Param("folderIds") java.util.Collection<Long> folderIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE folder SET parent_id = NULL WHERE folder_id IN (:folderIds)", nativeQuery = true)
    int clearParentByIds(@Param("folderIds") java.util.Collection<Long> folderIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM folder WHERE folder_id IN (:folderIds)", nativeQuery = true)
    int hardDeleteByIds(@Param("folderIds") java.util.Collection<Long> folderIds);

        // 워크스페이스의 루트 폴더들 조회
    List<Folder> findByWorkspaceWorkspaceIdAndParentIsNull(Long workspaceId);

    long countByWorkspace(Workspace workspace);

    // 회원 탈퇴 시 다른 워크스페이스에 만든 폴더의 작성자 정보를 비운다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Folder f SET f.user = null WHERE f.user.userId = :userId")
    void clearUser(@Param("userId") Long userId);

    /** 워크스페이스별 폴더 수 (휴지통 제외). [workspaceId, count] */
    @org.springframework.data.jpa.repository.Query("SELECT f.workspace.workspaceId, COUNT(f) FROM Folder f WHERE f.workspace.workspaceId IN :ids GROUP BY f.workspace.workspaceId")
    java.util.List<Object[]> countByWorkspaceIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

}
