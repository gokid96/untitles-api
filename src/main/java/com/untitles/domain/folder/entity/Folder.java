package com.untitles.domain.folder.entity;


import com.untitles.domain.post.entity.Post;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder

@Entity
@SQLRestriction("deleted_at IS NULL")  // 휴지통에 있는 폴더는 일반 조회에서 제외
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long folderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")  // 탈퇴한 사용자가 만든 폴더는 null
    private Users user;

    // 휴지통으로 이동한 시각 (null이면 정상 폴더)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
    @Builder.Default
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> children = new ArrayList<>();

    // 양방향 - 폴더 내 게시글들
    @BatchSize(size = 100)
    @Builder.Default
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

    // 정적 팩토리 메서드
    public static Folder create(Users user, Workspace workspace, Folder parent, String name) {
        return Folder.builder()
                .user(user)
                .workspace(workspace)
                .parent(parent)
                .name(name)
                .build();
    }
}