package com.untitles.domain.trash;

import com.untitles.domain.folder.dto.request.FolderCreateRequestDTO;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.folder.service.FolderService;
import com.untitles.domain.folder.service.WorkspaceTreeReader;
import com.untitles.domain.link.repository.PostLinkRepository;
import com.untitles.domain.link.service.PostLinkService;
import com.untitles.domain.post.dto.request.PostCreateRequestDTO;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.post.service.PostService;
import com.untitles.domain.trash.dto.TrashResponse;
import com.untitles.domain.trash.service.TrashService;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.HtmlSanitizer;
import com.untitles.global.util.WorkspaceMemberHelper;
import com.untitles.support.H2JpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@H2JpaTest
@Import({TrashService.class, PostService.class, PostLinkService.class, FolderService.class, WorkspaceTreeReader.class,
        HtmlSanitizer.class, WorkspaceMemberHelper.class})
class TrashServiceTest {

    @Autowired TrashService trashService;
    @Autowired PostService postService;
    @Autowired FolderService folderService;
    @Autowired PostRepository postRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired PostLinkRepository postLinkRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired EntityManager em;

    private Long userId;
    private Long ws;

    @BeforeEach
    void setUp() {
        Users user = userRepository.save(Users.createLocal("me@example.com", "me", "pw", "나"));
        Workspace w = workspaceRepository.save(Workspace.createPersonal());
        memberRepository.save(WorkspaceMember.createOwner(w, user));
        userId = user.getUserId();
        ws = w.getWorkspaceId();
    }

    private Long folder(String name, Long parentId) {
        return folderService.createFolder(userId, ws,
                new FolderCreateRequestDTO(name, parentId)).getFolderId();
    }

    private Long post(String title, Long folderId, String content) {
        return postService.createPost(userId, ws,
                PostCreateRequestDTO.builder().title(title).content(content).folderId(folderId).build()).getPostId();
    }

    private long countAll(String table) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("폴더를 지우면 하위 폴더·글이 함께 휴지통에 들어가고, 휴지통에는 최상위 폴더만 보인다")
    void trashFolderMovesSubtree() {
        Long a = folder("A", null);
        Long b = folder("B", a);
        post("a1", a, "<p>1</p>");
        post("b1", b, "<p>2</p>");
        Long keep = post("밖", null, "<p>3</p>");

        folderService.deleteFolder(userId, ws, a);

        assertThat(folderRepository.findAllByWorkspaceWorkspaceId(ws)).isEmpty();
        assertThat(postRepository.findAllByWorkspaceWorkspaceId(ws)).extracting("postId").containsExactly(keep);

        TrashResponse trash = trashService.getTrash(userId, ws);
        assertThat(trash.folders()).extracting(TrashResponse.Item::name).containsExactly("A");
        assertThat(trash.folders().get(0).postCount()).isEqualTo(2);
        assertThat(trash.posts()).isEmpty();
    }

    @Test
    @DisplayName("폴더를 복구하면 함께 지워진 항목만 돌아오고, 먼저 따로 지운 글은 휴지통에 남는다")
    void restoreFolderBringsBackSubtree() {
        Long a = folder("A", null);
        Long b = folder("B", a);
        Long early = post("먼저 지움", a, "<p>x</p>");
        post("b1", b, "<p>y</p>");

        postService.deletePost(userId, ws, early);
        em.createNativeQuery("UPDATE post SET deleted_at = :t WHERE post_id = :id")
                .setParameter("t", LocalDateTime.now().minusMinutes(5)).setParameter("id", early).executeUpdate();
        folderService.deleteFolder(userId, ws, a);

        trashService.restoreFolder(userId, ws, a);

        assertThat(folderRepository.findAllByWorkspaceWorkspaceId(ws)).extracting("name").containsExactlyInAnyOrder("A", "B");
        assertThat(postRepository.findAllByWorkspaceWorkspaceId(ws)).extracting("title").containsExactly("b1");
        assertThat(trashService.getTrash(userId, ws).posts()).extracting(TrashResponse.Item::name).containsExactly("먼저 지움");
    }

    @Test
    @DisplayName("폴더가 휴지통에 있는 글을 복구하면 최상위로 나온다")
    void restorePostFromTrashedFolderGoesToRoot() {
        Long a = folder("A", null);
        Long p = post("글", a, "<p>x</p>");
        postService.deletePost(userId, ws, p);
        folderService.deleteFolder(userId, ws, a);

        trashService.restorePost(userId, ws, p);

        assertThat(postRepository.findByWorkspaceWorkspaceIdAndFolderIsNull(ws)).extracting("postId").containsExactly(p);
    }

    @Test
    @DisplayName("휴지통의 폴더를 영구 삭제하면 안의 글과 링크도 지워진다")
    void deleteFolderPermanently() {
        Long a = folder("A", null);
        Long inner = post("안", a, "<p>x</p>");
        post("밖", null, "<p><a class=\"note-link\" data-note-id=\"" + inner + "\">안</a></p>");
        folderService.deleteFolder(userId, ws, a);

        trashService.deleteFolderPermanently(userId, ws, a);

        assertThat(countAll("folder")).isZero();
        assertThat(countAll("post")).isEqualTo(1);
        assertThat(postLinkRepository.findAllByWorkspaceId(ws)).isEmpty();
    }

    @Test
    @DisplayName("보관 기간이 지난 항목만 영구 삭제된다")
    void purgeExpiredOnlyOldItems() {
        Long old = post("오래됨", null, "<p>x</p>");
        Long recent = post("최근", null, "<p>y</p>");
        postService.deletePost(userId, ws, old);
        postService.deletePost(userId, ws, recent);
        em.createNativeQuery("UPDATE post SET deleted_at = :t WHERE post_id = :id")
                .setParameter("t", LocalDateTime.now().minusDays(31)).setParameter("id", old).executeUpdate();

        trashService.purgeExpired();

        assertThat(trashService.getTrash(userId, ws).posts()).extracting(TrashResponse.Item::name).containsExactly("최근");
        assertThat(countAll("post")).isEqualTo(1);
    }

    @Test
    @DisplayName("휴지통 비우기는 휴지통 항목만 지우고, 워크스페이스 삭제 쿼리는 휴지통 항목까지 지운다")
    void emptyTrashAndWorkspaceDeletion() {
        Long a = folder("A", null);
        post("안", a, "<p>x</p>");
        post("남김", null, "<p>y</p>");
        folderService.deleteFolder(userId, ws, a);

        trashService.emptyTrash(userId, ws);
        assertThat(countAll("folder")).isZero();
        assertThat(countAll("post")).isEqualTo(1);

        Long b = post("또", null, "<p>z</p>");
        postService.deletePost(userId, ws, b);
        postRepository.deleteByWorkspaceWorkspaceId(ws);
        assertThat(countAll("post")).isZero();
    }

    @Test
    @DisplayName("다른 워크스페이스의 휴지통 항목은 복구할 수 없다")
    void cannotRestoreOtherWorkspaceItem() {
        Long p = post("글", null, "<p>x</p>");
        postService.deletePost(userId, ws, p);

        Workspace other = workspaceRepository.save(Workspace.createTeam("팀", null));
        memberRepository.save(WorkspaceMember.createOwner(other, userRepository.findById(userId).orElseThrow()));

        assertThatThrownBy(() -> trashService.restorePost(userId, other.getWorkspaceId(), p))
                .extracting("errorCode").isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}
