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

    //삭제시 벌크 연산
    @Modifying
    @Query("DELETE FROM Post p WHERE p.workspace.workspaceId = :workspaceId")
    void deleteByWorkspaceWorkspaceId(@Param("workspaceId") Long workspaceId);


    // 워크스페이스 + 게시글 ID로 조회
    Optional<Post> findByPostIdAndWorkspaceWorkspaceId(Long postId, Long workspaceId);

    long countByWorkspace(Workspace workspace);

    List<Post> findAllByWorkspaceWorkspaceId(Long workspaceId);

    // 회원 탈퇴 시 다른 워크스페이스에 남긴 글의 작성자 정보를 비운다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.author = null WHERE p.author.userId = :userId")
    void clearAuthor(@Param("userId") Long userId);
}