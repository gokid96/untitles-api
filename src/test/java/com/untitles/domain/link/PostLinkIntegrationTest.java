package com.untitles.domain.link;

import com.untitles.domain.link.dto.GraphResponse;
import com.untitles.domain.link.repository.PostLinkRepository;
import com.untitles.domain.link.service.PostLinkService;
import com.untitles.domain.post.dto.request.PostCreateRequestDTO;
import com.untitles.domain.post.dto.request.PostUpdateRequestDTO;
import com.untitles.domain.post.service.PostService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@H2JpaTest
@Import({PostService.class, PostLinkService.class, HtmlSanitizer.class, WorkspaceMemberHelper.class,
        com.untitles.domain.trash.service.TrashService.class})
class PostLinkIntegrationTest {

    @Autowired PostService postService;
    @Autowired PostLinkService postLinkService;
    @Autowired PostLinkRepository postLinkRepository;
    @Autowired com.untitles.domain.trash.service.TrashService trashService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired EntityManager em;

    private Long userId;
    private Long ws1;
    private Long ws2;

    @BeforeEach
    void setUp() {
        Users user = userRepository.save(Users.createLocal("me@example.com", "me", "pw", "나"));
        Workspace a = workspaceRepository.save(Workspace.createPersonal());
        Workspace b = workspaceRepository.save(Workspace.createTeam("팀", null));
        memberRepository.save(WorkspaceMember.createOwner(a, user));
        memberRepository.save(WorkspaceMember.createOwner(b, user));
        userId = user.getUserId();
        ws1 = a.getWorkspaceId();
        ws2 = b.getWorkspaceId();
    }

    private Long create(Long workspaceId, String title, String content) {
        return postService.createPost(userId, workspaceId,
                PostCreateRequestDTO.builder().title(title).content(content).build()).getPostId();
    }

    private static String link(Long id, String text) {
        return "<a class=\"note-link\" data-note-id=\"" + id + "\">" + text + "</a>";
    }

    @Test
    @DisplayName("본문의 노트 링크를 저장하고, 다른 워크스페이스·자기 자신·없는 글은 제외한다")
    void syncsValidLinksOnly() {
        Long b = create(ws1, "B", "<p>b</p>");
        Long other = create(ws2, "다른 곳", "<p>x</p>");
        Long a = create(ws1, "A", "<p>a</p>");

        postService.updatePost(userId, ws1, a, PostUpdateRequestDTO.builder()
                .content("<p>" + link(b, "B") + link(other, "다른 곳") + link(a, "A") + link(99999L, "없음") + "</p>")
                .build());
        em.flush();

        GraphResponse graph = postLinkService.getGraph(userId, ws1);
        assertThat(graph.nodes()).extracting(GraphResponse.Node::title).containsExactlyInAnyOrder("A", "B");
        assertThat(graph.links()).containsExactly(new GraphResponse.Link(a, b));
    }

    @Test
    @DisplayName("본문에서 링크를 지우면 연결도 사라진다")
    void removingLinkUpdatesGraph() {
        Long b = create(ws1, "B", "<p>b</p>");
        Long a = create(ws1, "A", "<p>" + link(b, "B") + "</p>");
        assertThat(postLinkService.getGraph(userId, ws1).links()).hasSize(1);

        postService.updatePost(userId, ws1, a, PostUpdateRequestDTO.builder().content("<p>링크 없음</p>").build());
        em.flush();

        assertThat(postLinkService.getGraph(userId, ws1).links()).isEmpty();
    }

    @Test
    @DisplayName("링크된 글을 휴지통에 넣으면 그래프에서 빠지고, 영구 삭제하면 링크도 정리된다")
    void deletingTargetCleansLinks() {
        Long b = create(ws1, "B", "<p>b</p>");
        create(ws1, "A", "<p>" + link(b, "B") + "</p>");

        postService.deletePost(userId, ws1, b);
        assertThat(postLinkService.getGraph(userId, ws1).links()).isEmpty();
        assertThat(postLinkRepository.findAllByWorkspaceId(ws1)).hasSize(1);  // 복구 대비 보관

        trashService.deletePostPermanently(userId, ws1, b);
        em.flush();

        assertThat(postLinkRepository.findAllByWorkspaceId(ws1)).isEmpty();
    }

    @Test
    @DisplayName("저장 시 정화를 거쳐도 노트 링크 속성은 남고 위험한 속성은 제거된다")
    void sanitizerKeepsNoteLink() {
        String clean = new HtmlSanitizer().sanitize(
                "<p><a class=\"note-link\" data-note-id=\"12\" onclick=\"alert(1)\">노트</a></p>");

        assertThat(clean).contains("data-note-id=\"12\"").contains("note-link").doesNotContain("onclick");
    }

    @Test
    @DisplayName("멤버가 아니면 그래프를 볼 수 없다")
    void graphRequiresMembership() {
        Users stranger = userRepository.save(Users.createLocal("x@example.com", "stranger", "pw", "남"));

        assertThatThrownBy(() -> postLinkService.getGraph(stranger.getUserId(), ws1))
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
