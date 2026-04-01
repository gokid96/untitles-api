package com.untitles.domain.post.entity;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @Version
    private Long version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;  // 이 게시글이 속한 폴더

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    public void updateTitle(String Title) {
        this.title = Title;
    }
    public void updateContent(String content) {
        this.content = content;
    }
    public void updateFolder(Folder folder) {
        this.folder = folder;
    }

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublic = false;  // 개별 공개

    @Column(nullable = false)
    @Builder.Default
    private Boolean isExcluded = false;  // 전체공개 시 제외

    public void updateIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void updateIsExcluded(Boolean isExcluded) {
        this.isExcluded = isExcluded;
    }

    // 정적 팩토리 메서드
    public static Post create(String title, String sanitizedContent, Users author, Workspace workspace, Folder folder) {
        return Post.builder()
                .title(title)
                .content(sanitizedContent)
                .author(author)
                .workspace(workspace)
                .folder(folder)
                .build();
    }
}