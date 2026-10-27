package com.untitles.domain.collab.repository;

import com.untitles.domain.collab.entity.PostCollabState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostCollabStateRepository extends JpaRepository<PostCollabState, Long> {

    /** 영구 삭제된 노트의 편집 상태 정리 (휴지통에 있는 노트 것은 남긴다) */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM post_collab_state WHERE post_id NOT IN (SELECT post_id FROM post)", nativeQuery = true)
    int deleteOrphans();
}
