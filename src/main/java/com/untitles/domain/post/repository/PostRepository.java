package com.untitles.domain.post.repository;

import com.untitles.domain.post.entity.Post;
import com.untitles.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    // 워크스페이스 + 폴더 없는 게시글 (루트 게시글)
    List<Post> findByWorkspaceWorkspaceIdAndFolderIsNull(Long workspaceId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.author WHERE p.workspace.workspaceId = :workspaceId")
    List<Post> findAllWithAuthorByWorkspaceWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.author WHERE p.postId = :postId AND p.workspace.workspaceId = :workspaceId")
    Optional<Post> findWithAuthorByPostIdAndWorkspaceWorkspaceId(@Param("postId") Long postId, @Param("workspaceId") Long workspaceId);

    // 워크스페이스 삭제 시 벌크 연산 (휴지통에 있는 글까지 모두 삭제하려고 네이티브 쿼리 사용)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM post WHERE workspace_id = :workspaceId", nativeQuery = true)
    void deleteByWorkspaceWorkspaceId(@Param("workspaceId") Long workspaceId);

    // ===== 휴지통 (삭제된 글은 엔티티 조회에서 제외되므로 네이티브 쿼리로 다룬다) =====

    interface TrashedPostRow {
        Long getPostId();
        String getTitle();
        Long getFolderId();
        java.time.LocalDateTime getDeletedAt();
    }

    @Query(value = "SELECT post_id AS postId, title, folder_id AS folderId, deleted_at AS deletedAt "
            + "FROM post WHERE workspace_id = :workspaceId AND deleted_at IS NOT NULL", nativeQuery = true)
    List<TrashedPostRow> findTrashedByWorkspace(@Param("workspaceId") Long workspaceId);

    @Query(value = "SELECT post_id AS postId, title, folder_id AS folderId, deleted_at AS deletedAt "
            + "FROM post WHERE post_id = :postId AND workspace_id = :workspaceId AND deleted_at IS NOT NULL", nativeQuery = true)
    Optional<TrashedPostRow> findTrashedById(@Param("postId") Long postId, @Param("workspaceId") Long workspaceId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE post SET deleted_at = :deletedAt WHERE post_id = :postId AND deleted_at IS NULL", nativeQuery = true)
    int softDelete(@Param("postId") Long postId, @Param("deletedAt") java.time.LocalDateTime deletedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE post SET deleted_at = :deletedAt WHERE folder_id IN (:folderIds) AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteByFolderIds(@Param("folderIds") java.util.Collection<Long> folderIds, @Param("deletedAt") java.time.LocalDateTime deletedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE post SET deleted_at = NULL, folder_id = :folderId WHERE post_id = :postId", nativeQuery = true)
    int restore(@Param("postId") Long postId, @Param("folderId") Long folderId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE post SET deleted_at = NULL WHERE folder_id IN (:folderIds) AND deleted_at = :deletedAt", nativeQuery = true)
    int restoreByFolderIds(@Param("folderIds") java.util.Collection<Long> folderIds, @Param("deletedAt") java.time.LocalDateTime deletedAt);

    @Query(value = "SELECT post_id FROM post WHERE folder_id IN (:folderIds)", nativeQuery = true)
    List<Long> findAllIdsByFolderIds(@Param("folderIds") java.util.Collection<Long> folderIds);

    @Query(value = "SELECT post_id FROM post WHERE deleted_at IS NOT NULL AND deleted_at < :before", nativeQuery = true)
    List<Long> findIdsTrashedBefore(@Param("before") java.time.LocalDateTime before);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM post WHERE post_id IN (:postIds)", nativeQuery = true)
    int hardDeleteByIds(@Param("postIds") java.util.Collection<Long> postIds);


    // 워크스페이스 + 게시글 ID로 조회
    Optional<Post> findByPostIdAndWorkspaceWorkspaceId(Long postId, Long workspaceId);

    long countByWorkspace(Workspace workspace);

    List<Post> findAllByWorkspaceWorkspaceId(Long workspaceId);

    // 회원 탈퇴 시 다른 워크스페이스에 남긴 글의 작성자 정보를 비운다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.author = null WHERE p.author.userId = :userId")
    void clearAuthor(@Param("userId") Long userId);

    /**
     * 예전에 저장 과정에서 빠진 체크리스트 항목 표시(data-type="taskItem")를 되살린다.
     * 휴지통에 있는 글도 포함하며, 이미 고친 글은 다시 건드리지 않는다.
     */
    @org.springframework.transaction.annotation.Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE post SET content = REPLACE(content, '<li data-checked=', '<li data-type=\"taskItem\" data-checked=') " +
            "WHERE content LIKE '%<li data-checked=%'", nativeQuery = true)
    int repairChecklistItems();
}