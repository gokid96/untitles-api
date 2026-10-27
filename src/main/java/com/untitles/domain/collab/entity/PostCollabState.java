package com.untitles.domain.collab.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 실시간 공동 편집(Yjs) 문서 상태. 노트 본문(HTML)은 post에 그대로 두고, 편집 이력이 담긴 Yjs 상태만 따로 보관한다.
 * contentHash는 이 상태를 저장할 때의 post 제목·본문 해시로, post가 다른 경로(예전 앱의 저장 등)로 바뀌면
 * 해시가 달라져 이 상태를 버리고 HTML에서 다시 만든다.
 */
@Entity
@Table(name = "post_collab_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCollabState {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Lob
    @Column(name = "ydoc", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] ydoc;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static PostCollabState of(Long postId, byte[] ydoc, String contentHash) {
        PostCollabState state = new PostCollabState();
        state.postId = postId;
        state.ydoc = ydoc;
        state.contentHash = contentHash;
        return state;
    }

    public void update(byte[] ydoc, String contentHash) {
        this.ydoc = ydoc;
        this.contentHash = contentHash;
    }
}
