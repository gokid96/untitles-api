package com.untitles.domain.collab;

import com.untitles.domain.collab.dto.CollabDtos.DocumentResponse;
import com.untitles.domain.collab.dto.CollabDtos.StoreResponse;
import com.untitles.domain.collab.service.CollabService;
import com.untitles.domain.link.repository.PostLinkRepository;
import com.untitles.domain.link.service.PostLinkService;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.util.HtmlSanitizer;
import com.untitles.global.util.WorkspaceMemberHelper;
import com.untitles.support.H2JpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@H2JpaTest
@Import({CollabService.class, PostLinkService.class, HtmlSanitizer.class, WorkspaceMemberHelper.class, com.untitles.domain.collab.live.WorkspaceLiveNotifier.class, CollabServiceTest.Caches.class})
class CollabServiceTest {

    @Autowired CollabService collabService;
    @Autowired PostRepository postRepository;
    @Autowired PostLinkRepository postLinkRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired EntityManager em;

    private Users owner;
    private Workspace ws;
    private Post post;
    private Post other;

    private static final String YDOC = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        owner = userRepository.save(Users.createLocal(null, "owner", "pw", "주인"));
        ws = workspaceRepository.save(Workspace.createTeam("팀", null));
        memberRepository.save(WorkspaceMember.createOwner(ws, owner));
        post = postRepository.save(Post.create("제목", "<p>본문</p>", owner, ws, null));
        other = postRepository.save(Post.create("다른 노트", "<p>x</p>", owner, ws, null));
    }

    @Test
    @DisplayName("멤버는 읽기·쓰기, 뷰어는 읽기만, 남은 접근 불가")
    void access() {
        Users viewer = userRepository.save(Users.createLocal(null, "viewer", "pw", "뷰어"));
        WorkspaceMember vm = WorkspaceMember.createOwner(ws, viewer);
        memberRepository.save(vm);
        em.createQuery("UPDATE WorkspaceMember m SET m.role = :role WHERE m.user.userId = :uid")
                .setParameter("role", WorkspaceRole.VIEWER).setParameter("uid", viewer.getUserId()).executeUpdate();
        em.clear();
        Users stranger = userRepository.save(Users.createLocal(null, "stranger", "pw", "남"));

        assertThat(collabService.access(owner.getUserId(), ws.getWorkspaceId(), post.getPostId()).canWrite()).isTrue();
        var v = collabService.access(viewer.getUserId(), ws.getWorkspaceId(), post.getPostId());
        assertThat(v.canRead()).isTrue();
        assertThat(v.canWrite()).isFalse();
        assertThat(collabService.access(stranger.getUserId(), ws.getWorkspaceId(), post.getPostId()).canRead()).isFalse();
        // 다른 워크스페이스의 노트 번호로는 접근 불가
        assertThat(collabService.access(owner.getUserId(), ws.getWorkspaceId() + 999, post.getPostId()).canRead()).isFalse();
    }

    @Test
    @DisplayName("저장하면 본문은 정화해서 저장하고 링크를 갱신하며, 다음 불러오기에는 저장한 Y 상태를 준다")
    void storeThenLoad() {
        DocumentResponse first = collabService.load(ws.getWorkspaceId(), post.getPostId());
        assertThat(first.ydoc()).isNull();

        Long versionBefore = post.getVersion();
        String content = "<p>새 본문 <a class=\"note-link\" data-note-id=\"" + other.getPostId() + "\">다른 노트</a></p><script>alert(1)</script>";
        StoreResponse stored = collabService.store(ws.getWorkspaceId(), post.getPostId(), "새 제목", content, YDOC, first.hash());

        em.flush();
        em.clear();
        Post saved = postRepository.findById(post.getPostId()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("새 제목");
        assertThat(saved.getContent()).doesNotContain("script").contains("새 본문");
        assertThat(saved.getVersion()).isGreaterThan(versionBefore);  // 예전 방식 클라이언트는 충돌로 감지
        assertThat(postLinkRepository.findAll()).anyMatch(l -> l.getTargetPostId().equals(other.getPostId()));

        DocumentResponse second = collabService.load(ws.getWorkspaceId(), post.getPostId());
        assertThat(second.hash()).isEqualTo(stored.hash());
        assertThat(second.ydoc()).isEqualTo(YDOC);
    }

    @Test
    @DisplayName("다른 경로로 노트가 바뀌면 저장을 거부(409)하고, 저장된 Y 상태도 더는 쓰지 않는다")
    void staleBase() {
        DocumentResponse loaded = collabService.load(ws.getWorkspaceId(), post.getPostId());
        collabService.store(ws.getWorkspaceId(), post.getPostId(), "제목", "<p>본문</p>", YDOC, loaded.hash());
        em.flush();
        em.clear();

        // 예전 앱이 일반 저장으로 본문을 바꿈
        Post p = postRepository.findById(post.getPostId()).orElseThrow();
        p.updateContent("<p>예전 앱에서 고침</p>");
        postRepository.saveAndFlush(p);

        DocumentResponse reloaded = collabService.load(ws.getWorkspaceId(), post.getPostId());
        assertThat(reloaded.ydoc()).isNull();  // HTML에서 다시 만들게 함
        assertThatThrownBy(() -> collabService.store(ws.getWorkspaceId(), post.getPostId(), "제목", "<p>실시간</p>", YDOC, loaded.hash()))
                .isInstanceOf(CollabService.StaleBaseException.class)
                .satisfies(e -> assertThat(((CollabService.StaleBaseException) e).getCurrent().content()).contains("예전 앱에서 고침"));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class Caches {
        @org.springframework.context.annotation.Bean
        org.springframework.cache.CacheManager cacheManager() {
            return new org.springframework.cache.concurrent.ConcurrentMapCacheManager("workspaceTree", "publicWorkspace", "publicPost");
        }
    }
}
