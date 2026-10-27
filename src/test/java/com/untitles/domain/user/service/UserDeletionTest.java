package com.untitles.domain.user.service;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.image.service.ImageService;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.domain.workspace.service.WorkspaceService;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.WorkspaceMemberHelper;
import com.untitles.support.H2JpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@H2JpaTest
@Import({UserService.class, WorkspaceService.class, WorkspaceMemberHelper.class})
class UserDeletionTest {

    @MockitoBean ImageService imageService;
    @MockitoBean PasswordEncoder passwordEncoder;
    @MockitoBean com.untitles.domain.auth.service.RefreshTokenService refreshTokenService;
    @MockitoBean com.untitles.domain.auth.social.AppleAuthClient appleAuthClient;
    @MockitoBean com.untitles.domain.email.service.EmailService emailService;
    @MockitoBean com.untitles.domain.collab.live.WorkspaceLiveNotifier liveNotifier;

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired PostRepository postRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired EntityManager em;

    private Users newUser(String loginId) {
        Users user = userRepository.save(Users.createLocal(loginId + "@example.com", loginId, "pw", loginId));
        Workspace personal = workspaceRepository.save(Workspace.createPersonal());
        memberRepository.save(WorkspaceMember.createOwner(personal, user));
        return user;
    }

    private Workspace teamOwnedBy(Users owner) {
        Workspace team = workspaceRepository.save(Workspace.createTeam("팀", null));
        memberRepository.save(WorkspaceMember.createOwner(team, owner));
        return team;
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("개인 워크스페이스와 글·폴더까지 함께 지우고 탈퇴한다")
    void deletesPersonalData() {
        Users me = newUser("me");
        Workspace personal = memberRepository.findAllByUserUserId(me.getUserId()).get(0).getWorkspace();
        Folder folder = folderRepository.save(Folder.create(me, personal, null, "폴더"));
        postRepository.save(Post.create("글", "<p>x</p>", me, personal, folder));
        flushClear();

        userService.deleteUser(me.getUserId());
        flushClear();

        assertThat(userRepository.findById(me.getUserId())).isEmpty();
        assertThat(workspaceRepository.findById(personal.getWorkspaceId())).isEmpty();
        assertThat(postRepository.count()).isZero();
        assertThat(folderRepository.count()).isZero();
    }

    @Test
    @DisplayName("다른 사람 워크스페이스에 쓴 글은 남기고 작성자만 비운다")
    void keepsPostsInOthersWorkspace() {
        Users owner = newUser("owner");
        Users me = newUser("me");
        Workspace team = teamOwnedBy(owner);
        memberRepository.save(WorkspaceMember.createMember(team, me, WorkspaceRole.MEMBER));
        Post post = postRepository.save(Post.create("팀 글", "<p>x</p>", me, team, null));
        flushClear();

        userService.deleteUser(me.getUserId());
        flushClear();

        Post kept = postRepository.findById(post.getPostId()).orElseThrow();
        assertThat(kept.getAuthor()).isNull();
        assertThat(memberRepository.countByWorkspace(team)).isEqualTo(1);
        assertThat(userRepository.findById(owner.getUserId())).isPresent();
    }

    @Test
    @DisplayName("다른 멤버가 있는 팀 워크스페이스의 소유자는 탈퇴할 수 없다")
    void ownerWithMembersCannotLeave() {
        Users me = newUser("me");
        Users other = newUser("other");
        Workspace team = teamOwnedBy(me);
        memberRepository.save(WorkspaceMember.createMember(team, other, WorkspaceRole.MEMBER));
        flushClear();

        assertThatThrownBy(() -> userService.deleteUser(me.getUserId()))
                .extracting("errorCode").isEqualTo(ErrorCode.OWNER_HAS_TEAM_MEMBERS);
    }

    @Test
    @DisplayName("혼자 쓰는 팀 워크스페이스는 함께 삭제된다")
    void deletesSoloTeamWorkspace() {
        Users me = newUser("me");
        Workspace team = teamOwnedBy(me);
        postRepository.save(Post.create("글", "<p>x</p>", me, team, null));
        flushClear();

        userService.deleteUser(me.getUserId());
        flushClear();

        assertThat(workspaceRepository.findById(team.getWorkspaceId())).isEmpty();
    }
}
