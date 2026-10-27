package com.untitles.domain.invitation;

import com.untitles.domain.email.service.EmailSender;
import com.untitles.domain.invitation.dto.InvitationCreateRequest;
import com.untitles.domain.invitation.repository.WorkspaceInvitationRepository;
import com.untitles.domain.invitation.service.InvitationService;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.WorkspaceMemberHelper;
import com.untitles.support.H2JpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@H2JpaTest
@Import({InvitationService.class, WorkspaceMemberHelper.class})
class InvitationServiceTest {

    @Autowired InvitationService invitationService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired WorkspaceInvitationRepository invitationRepository;
    @MockitoBean EmailSender emailSender;
    @MockitoBean com.untitles.domain.collab.live.WorkspaceLiveNotifier liveNotifier;

    Users owner;
    Users friend;
    Workspace team;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(Users.createWithEmail("owner@example.com", "hashed", "주인"));
        friend = userRepository.save(Users.createWithEmail("friend@example.com", "hashed", "친구"));
        team = workspaceRepository.save(Workspace.createTeam("팀", null));
        memberRepository.save(WorkspaceMember.createOwner(team, owner));
    }

    private InvitationCreateRequest byUser(Users user) {
        return new InvitationCreateRequest(user.getUserId(), null, WorkspaceRole.MEMBER);
    }

    @Test
    @DisplayName("초대만으로는 멤버가 되지 않고, 받은 사람이 수락해야 멤버가 된다")
    void acceptMakesMember() {
        invitationService.invite(owner.getUserId(), team.getWorkspaceId(), byUser(friend));
        assertThat(memberRepository.countByWorkspace(team)).isEqualTo(1);

        var mine = invitationService.mine(friend.getUserId());
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).workspaceName()).isEqualTo("팀");
        assertThat(mine.get(0).inviterNickname()).isEqualTo("주인");

        invitationService.accept(friend.getUserId(), mine.get(0).token());

        assertThat(memberRepository.findByWorkspaceWorkspaceIdAndUserUserId(team.getWorkspaceId(), friend.getUserId()))
                .get().extracting(WorkspaceMember::getRole).isEqualTo(WorkspaceRole.MEMBER);
        assertThat(invitationService.mine(friend.getUserId())).isEmpty();
    }

    @Test
    @DisplayName("거절하면 초대가 사라지고 멤버가 되지 않는다")
    void decline() {
        invitationService.invite(owner.getUserId(), team.getWorkspaceId(), byUser(friend));
        String token = invitationService.mine(friend.getUserId()).get(0).token();

        invitationService.decline(friend.getUserId(), token);

        assertThat(invitationService.mine(friend.getUserId())).isEmpty();
        assertThat(memberRepository.countByWorkspace(team)).isEqualTo(1);
        assertThatThrownBy(() -> invitationService.accept(friend.getUserId(), token))
                .extracting("errorCode").isEqualTo(ErrorCode.INVITATION_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사람은 남의 초대를 수락할 수 없다")
    void onlyInvitee() {
        Users stranger = userRepository.save(Users.createWithEmail("stranger@example.com", "hashed", "모르는사람"));
        invitationService.invite(owner.getUserId(), team.getWorkspaceId(), byUser(friend));
        String token = invitationService.mine(friend.getUserId()).get(0).token();

        assertThatThrownBy(() -> invitationService.accept(stranger.getUserId(), token))
                .extracting("errorCode").isEqualTo(ErrorCode.INVITATION_NOT_FOR_YOU);
    }

    @Test
    @DisplayName("가입하지 않은 이메일로 초대하면, 그 이메일로 가입(확인)한 계정이 받는다")
    void emailInvite() {
        invitationService.invite(owner.getUserId(), team.getWorkspaceId(),
                new InvitationCreateRequest(null, "New@Example.com", WorkspaceRole.VIEWER));

        Users joined = userRepository.save(Users.createWithEmail("new@example.com", "hashed", "새사람"));
        var mine = invitationService.mine(joined.getUserId());
        assertThat(mine).hasSize(1);

        // 같은 이메일을 확인하지 않은 계정은 받을 수 없다
        Users unverified = userRepository.save(Users.createLocal("new2@example.com", "n2", "hashed", "미확인"));
        assertThat(invitationService.mine(unverified.getUserId())).isEmpty();

        invitationService.accept(joined.getUserId(), mine.get(0).token());
        assertThat(memberRepository.findByWorkspaceWorkspaceIdAndUserUserId(team.getWorkspaceId(), joined.getUserId()))
                .get().extracting(WorkspaceMember::getRole).isEqualTo(WorkspaceRole.VIEWER);
    }

    @Test
    @DisplayName("같은 사람을 다시 초대하면 새로 만들지 않고 권한·기한을 갱신한다")
    void reinvite() {
        invitationService.invite(owner.getUserId(), team.getWorkspaceId(), byUser(friend));
        invitationService.invite(owner.getUserId(), team.getWorkspaceId(),
                new InvitationCreateRequest(friend.getUserId(), null, WorkspaceRole.ADMIN));

        var pending = invitationService.pendingForWorkspace(owner.getUserId(), team.getWorkspaceId());
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).role()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(pending.get(0).email()).isEqualTo("fr***@example.com");
    }

    @Test
    @DisplayName("멤버 수 제한(5명)에 대기 중인 초대도 포함한다")
    void limitIncludesPending() {
        for (int i = 0; i < 4; i++) {
            invitationService.invite(owner.getUserId(), team.getWorkspaceId(),
                    new InvitationCreateRequest(null, "p" + i + "@example.com", WorkspaceRole.MEMBER));
        }
        assertThatThrownBy(() -> invitationService.invite(owner.getUserId(), team.getWorkspaceId(), byUser(friend)))
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_LIMIT_EXCEEDED);

        // 취소하면 자리가 생긴다
        var first = invitationService.pendingForWorkspace(owner.getUserId(), team.getWorkspaceId()).get(0);
        invitationService.cancel(owner.getUserId(), team.getWorkspaceId(), first.invitationId());
        invitationService.invite(owner.getUserId(), team.getWorkspaceId(), byUser(friend));
    }

    @Test
    @DisplayName("이미 멤버이거나 자기 자신이면 초대할 수 없고, 일반 멤버는 초대할 수 없다")
    void rules() {
        assertThatThrownBy(() -> invitationService.invite(owner.getUserId(), team.getWorkspaceId(), byUser(owner)))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_INVITE_SELF);

        memberRepository.save(WorkspaceMember.createMember(team, friend, WorkspaceRole.MEMBER));
        assertThatThrownBy(() -> invitationService.invite(owner.getUserId(), team.getWorkspaceId(), byUser(friend)))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_WORKSPACE_MEMBER);

        Users other = userRepository.save(Users.createWithEmail("o@example.com", "hashed", "다른"));
        assertThatThrownBy(() -> invitationService.invite(friend.getUserId(), team.getWorkspaceId(), byUser(other)))
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_PERMISSION);
    }

    @Test
    @DisplayName("초대 링크 미리보기는 이메일 일부를 가린다")
    void preview() {
        invitationService.invite(owner.getUserId(), team.getWorkspaceId(), byUser(friend));
        String token = invitationRepository.findAll().get(0).getToken();

        var preview = invitationService.preview(token);
        assertThat(preview.workspaceName()).isEqualTo("팀");
        assertThat(preview.inviterNickname()).isEqualTo("주인");
        assertThat(preview.maskedEmail()).isEqualTo("fr***@example.com");
    }
}
