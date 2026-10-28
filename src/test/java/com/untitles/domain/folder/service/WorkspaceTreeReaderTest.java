package com.untitles.domain.folder.service;

import com.untitles.domain.folder.dto.response.FolderResponseDTO;
import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.dto.response.PostSimpleDTO;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.support.H2JpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@H2JpaTest
@Import(WorkspaceTreeReader.class)
class WorkspaceTreeReaderTest {

    @Autowired WorkspaceTreeReader reader;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired PostRepository postRepository;
    @Autowired EntityManager em;

    @Test
    @DisplayName("중첩 폴더·폴더 안 노트·맨 위 노트를 본문 없이 트리로 만들고, 휴지통 항목은 뺀다")
    void buildsTree() {
        Users user = userRepository.save(Users.createWithEmail("t@example.com", "hashed", "트리"));
        Workspace ws = workspaceRepository.save(Workspace.createTeam("팀", null));
        Folder top = folderRepository.save(Folder.create(user, ws, null, "위"));
        Folder child = folderRepository.save(Folder.create(user, ws, top, "아래"));
        Folder trashed = folderRepository.save(Folder.create(user, ws, null, "휴지통 폴더"));
        postRepository.save(Post.create("맨 위 노트", "<p>a</p>", user, ws, null));
        postRepository.save(Post.create("위 폴더 노트", "<p>b</p>", user, ws, top));
        postRepository.save(Post.create("아래 폴더 노트", "<p>c</p>", user, ws, child));
        Post trashedPost = postRepository.save(Post.create("휴지통 노트", "<p>d</p>", user, ws, null));
        em.flush();
        em.createNativeQuery("UPDATE folder SET deleted_at = :now WHERE folder_id = :id")
                .setParameter("now", LocalDateTime.now()).setParameter("id", trashed.getFolderId()).executeUpdate();
        em.createNativeQuery("UPDATE post SET deleted_at = :now WHERE post_id = :id")
                .setParameter("now", LocalDateTime.now()).setParameter("id", trashedPost.getPostId()).executeUpdate();
        em.clear();

        var tree = reader.getTree(ws.getWorkspaceId());

        assertThat(tree.getRootPosts()).extracting(PostSimpleDTO::getTitle).containsExactly("맨 위 노트");
        assertThat(tree.getFolders()).extracting(FolderResponseDTO::getName).containsExactly("위");
        FolderResponseDTO topDto = tree.getFolders().get(0);
        assertThat(topDto.getPosts()).extracting(PostSimpleDTO::getTitle).containsExactly("위 폴더 노트");
        assertThat(topDto.getChildren()).extracting(FolderResponseDTO::getName).containsExactly("아래");
        assertThat(topDto.getChildren().get(0).getPosts()).extracting(PostSimpleDTO::getTitle).containsExactly("아래 폴더 노트");
        assertThat(topDto.getChildren().get(0).getParentId()).isEqualTo(top.getFolderId());
    }
}
