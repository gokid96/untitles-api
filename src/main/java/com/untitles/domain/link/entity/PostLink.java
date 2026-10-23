package com.untitles.domain.link.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 노트 간 직접 링크 ([[노트]]로 만든 링크)
 * 본문 HTML의 <a class="note-link" data-note-id="..."> 에서 저장 시점에 추출한다.
 * 글 삭제·워크스페이스 삭제 시 함께 정리하므로 FK 없이 ID만 보관한다.
 */
@Entity
@Table(name = "post_link",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source_post_id", "target_post_id"}),
        indexes = {
                @Index(name = "idx_post_link_workspace", columnList = "workspace_id"),
                @Index(name = "idx_post_link_target", columnList = "target_post_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "source_post_id", nullable = false)
    private Long sourcePostId;

    @Column(name = "target_post_id", nullable = false)
    private Long targetPostId;

    public static PostLink of(Long workspaceId, Long sourcePostId, Long targetPostId) {
        PostLink link = new PostLink();
        link.workspaceId = workspaceId;
        link.sourcePostId = sourcePostId;
        link.targetPostId = targetPostId;
        return link;
    }
}
