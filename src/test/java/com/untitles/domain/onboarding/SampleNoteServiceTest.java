package com.untitles.domain.onboarding;

import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.link.repository.PostLinkRepository;
import com.untitles.domain.link.service.PostLinkService;
import com.untitles.domain.onboarding.service.SampleNoteService;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.util.HtmlSanitizer;
import com.untitles.support.H2JpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@H2JpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SampleNoteService.class, PostLinkService.class, HtmlSanitizer.class,
        com.untitles.global.util.WorkspaceMemberHelper.class})
class SampleNoteServiceTest {

    @Autowired SampleNoteService sampleNoteService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostLinkRepository postLinkRepository;
    @Autowired EntityManager em;

    @Test
    @DisplayName("새 워크스페이스에 시작하기 폴더와 서로 연결된 예시 노트 3개를 만들고, 다시 불러도 중복되지 않는다")
    void createsLinkedSampleNotesOnce() {
        String suffix = String.valueOf(System.nanoTime());
        Users user = userRepository.save(Users.createLocal(null, "u" + suffix.substring(suffix.length() - 8), "pw", "n" + suffix.substring(suffix.length() - 8)));
        Workspace ws = workspaceRepository.save(Workspace.createPersonal());
        memberRepository.save(WorkspaceMember.createOwner(ws, user));

        sampleNoteService.createSampleNotes(user.getUserId(), ws.getWorkspaceId());
        sampleNoteService.createSampleNotes(user.getUserId(), ws.getWorkspaceId());

        List<Post> posts = postRepository.findAll().stream()
                .filter(p -> p.getWorkspace().getWorkspaceId().equals(ws.getWorkspaceId()))
                .toList();
        assertThat(posts).hasSize(3);
        assertThat(posts).allSatisfy(p -> {
            assertThat(p.getFolder()).isNotNull();
            assertThat(p.getContent()).contains("data-note-id=");
        });

        Post welcome = posts.stream().filter(p -> p.getTitle().contains("환영")).findFirst().orElseThrow();
        System.out.println(welcome.getContent());
        // 체크리스트가 저장 후에도 체크리스트로 남아야 한다
        assertThat(welcome.getContent()).contains("data-type=\"taskList\"").contains("data-type=\"taskItem\"").contains("data-checked=\"true\"");

        long links = postLinkRepository.findAll().stream()
                .filter(l -> posts.stream().anyMatch(p -> p.getPostId().equals(l.getSourcePostId())))
                .count();
        assertThat(links).isEqualTo(5);
    }
}
