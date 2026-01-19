package com.untitles.domain.folder.entity;


import com.untitles.domain.post.entity.Post;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/*
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
* */
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
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> children = new ArrayList<>();

    // 양방향 - 폴더 내 게시글들
    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL)
    private List<Post> posts = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;



    @Builder
    public Folder(Users user, Folder parent, String name, Integer orderIndex, Workspace workspace) {
        this.user = user;
        this.parent = parent;
        this.name = name;
        this.workspace = workspace;
    }

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

    /*
     * 루트 폴더 여부 확인
     * */
    public boolean isRoot() {
        return this.parent == null;
    }

    /*
     * 폴더 depth 계산 (루트 = 0)
     * */
    public int getDepth() {
        int depth = 0;
        Folder current = this.parent;
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

}