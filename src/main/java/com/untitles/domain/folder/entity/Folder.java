package com.untitles.domain.folder.entity;


import com.untitles.domain.post.entity.Post;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long folderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Folder parent;  // 상위 폴더 (null이면 루트 폴더)

    @Column(nullable = false, length = 50)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 양방향 - 하위 폴더들
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> children = new ArrayList<>();

    // 양방향 - 폴더 내 게시글들
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Post> posts = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    /*
     * 폴더명 수정
     * */
    public void updateName(String name) {
        this.name = name;
    }

    /*
     * 상위 폴더 변경 (폴더 이동)
     * */
    public void moveToParent(Folder newParent) {  // 이름 변경
        // 기존 부모에서 제거
        if (this.parent != null) {
            this.parent.children.remove(this);
        }
        // 새 부모로 이동
        this.parent = newParent;
        // 새 부모의 자식 목록에 추가
        if (newParent != null) {
            newParent.getChildren().add(this);
        }
    }


    @Column(nullable = false)
    @Builder.Default
    private Boolean publishAll = false;  // 폴더 내 전체 공개

    public void updatePublishAll(Boolean publishAll) {
        this.publishAll = publishAll;
    }

}