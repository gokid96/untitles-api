package com.untitles.domain.link.repository;

import com.untitles.domain.link.entity.PostLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostLinkRepository extends JpaRepository<PostLink, Long> {

    List<PostLink> findAllByWorkspaceId(Long workspaceId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PostLink l WHERE l.sourcePostId = :postId")
    void deleteBySourcePostId(@Param("postId") Long postId);

    // 글 삭제 시: 이 글에서 나가는 링크와 이 글로 들어오는 링크 모두 삭제
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PostLink l WHERE l.sourcePostId = :postId OR l.targetPostId = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PostLink l WHERE l.workspaceId = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
