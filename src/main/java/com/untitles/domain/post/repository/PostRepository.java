package com.untitles.domain.post.repository;

import com.untitles.domain.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByAuthor_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Optional<Post> findByAuthor_UserIdAndPostId(Long userId, Long postId);
    // 워크스페이스의 모든 게시글 조회
    List<Post> findByWorkspaceWorkspaceId(Long workspaceId);

    // 워크스페이스 + 폴더의 게시글 조회
    List<Post> findByWorkspaceWorkspaceIdAndFolderFolderId(Long workspaceId, Long folderId);

    // 워크스페이스 + 폴더 없는 게시글 (루트 게시글)
    List<Post> findByWorkspaceWorkspaceIdAndFolderIsNull(Long workspaceId);

    // 워크스페이스 + 게시글 ID로 조회
    Optional<Post> findByPostIdAndWorkspaceWorkspaceId(Long postId, Long workspaceId);
}