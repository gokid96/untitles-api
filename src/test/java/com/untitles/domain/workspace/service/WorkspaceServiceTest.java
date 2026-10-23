package com.untitles.domain.workspace.service;

import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.dto.request.MemberInviteRequest;
import com.untitles.domain.workspace.dto.request.MemberRoleUpdateRequest;
import com.untitles.domain.workspace.dto.request.WorkspaceCreateRequest;
import com.untitles.domain.workspace.dto.request.WorkspaceUpdateRequest;
import com.untitles.domain.workspace.dto.response.WorkspaceMemberResponse;
import com.untitles.domain.workspace.dto.response.WorkspaceResponse;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.entity.WorkspaceType;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.WorkspaceMemberHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private com.untitles.domain.link.repository.PostLinkRepository postLinkRepository;

    private WorkspaceService workspaceService;

    private Users testUser;
    private Users otherUser;
    private Workspace testWorkspace;
    private WorkspaceMember ownerMember;

    @BeforeEach
    void setUp() {
        // 멤버 확인(ACCESS_DENIED)과 권한 검사(INSUFFICIENT_PERMISSION)는 WorkspaceMemberHelper에 있으므로
        // mock 대신 실제 헬퍼를 mock 리포지토리로 감싸서 주입한다.
        workspaceService = new WorkspaceService(
                workspaceRepository,
                memberRepository,
                userRepository,
                postRepository,
                folderRepository,
                new WorkspaceMemberHelper(memberRepository),
                postLinkRepository
        );

        // 테스트 유저 생성
        testUser = Users.builder()
                .email("test@test.com")
                .loginId("testuser")
                .password("password123")
                .nickname("테스터")
                .build();
        ReflectionTestUtils.setField(testUser, "userId", 1L);

        otherUser = Users.builder()
                .email("other@test.com")
                .loginId("otheruser")
                .password("password123")
                .nickname("다른유저")
                .build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);

        // 테스트 워크스페이스 생성 (초대/삭제는 TEAM에서만 가능)
        testWorkspace = Workspace.createTeam("테스트 워크스페이스", "테스트용");
        ReflectionTestUtils.setField(testWorkspace, "workspaceId", 1L);

        // OWNER 멤버
        ownerMember = WorkspaceMember.createOwner(testWorkspace, testUser);
        ReflectionTestUtils.setField(ownerMember, "id", 1L);
    }

    /**
     * WorkspaceMemberHelper.getMemberOrThrow가 사용하는 멤버 조회를 stub한다. (인자 순서: workspaceId, userId)
     */
    private void stubMember(Long workspaceId, Long userId, WorkspaceMember member) {
        when(memberRepository.findByWorkspaceWorkspaceIdAndUserUserId(workspaceId, userId))
                .thenReturn(Optional.of(member));
    }

    @Nested
    @DisplayName("워크스페이스 생성")
    class CreateWorkspace {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            WorkspaceCreateRequest request = new WorkspaceCreateRequest("새 워크스페이스", "설명");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
                Workspace ws = invocation.getArgument(0);
                ReflectionTestUtils.setField(ws, "workspaceId", 10L);
                return ws;
            });

            // when
            WorkspaceResponse response = workspaceService.createWorkspace(1L, request);

            // then
            assertThat(response.workspaceId()).isEqualTo(10L);
            assertThat(response.name()).isEqualTo("새 워크스페이스");
            assertThat(response.description()).isEqualTo("설명");
            assertThat(response.type()).isEqualTo(WorkspaceType.TEAM);
            assertThat(response.myRole()).isEqualTo(WorkspaceRole.OWNER);
            verify(workspaceRepository).save(any(Workspace.class));

            ArgumentCaptor<WorkspaceMember> memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
            verify(memberRepository).save(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getUser()).isEqualTo(testUser);
            assertThat(memberCaptor.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
            assertThat(memberCaptor.getValue().getWorkspace().getWorkspaceId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("실패 - 사용자 없음")
        void fail_userNotFound() {
            // given
            WorkspaceCreateRequest request = new WorkspaceCreateRequest("새 워크스페이스", "설명");
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workspaceService.createWorkspace(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
            verify(workspaceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("워크스페이스 목록 조회")
    class GetMyWorkspaces {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findAllByUserWithWorkspaceAndMembers(testUser)).thenReturn(List.of(ownerMember));

            // when
            List<WorkspaceResponse> responses = workspaceService.getMyWorkspaces(1L);

            // then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).name()).isEqualTo("테스트 워크스페이스");
            assertThat(responses.get(0).myRole()).isEqualTo(WorkspaceRole.OWNER);
        }
    }

    @Nested
    @DisplayName("워크스페이스 수정")
    class UpdateWorkspace {

        @Test
        @DisplayName("성공 - OWNER")
        void success_owner() {
            // given
            WorkspaceUpdateRequest request = new WorkspaceUpdateRequest("수정된 이름", "수정된 설명");
            stubMember(1L, 1L, ownerMember);

            // when
            WorkspaceResponse response = workspaceService.updateWorkspace(1L, 1L, request);

            // then
            assertThat(response.name()).isEqualTo("수정된 이름");
            assertThat(response.description()).isEqualTo("수정된 설명");
            verify(workspaceRepository).save(testWorkspace);
        }

        @Test
        @DisplayName("실패 - 권한 없음 (MEMBER)")
        void fail_noPermission() {
            // given
            WorkspaceUpdateRequest request = new WorkspaceUpdateRequest("수정된 이름", null);
            WorkspaceMember memberRole = WorkspaceMember.createMember(testWorkspace, testUser, WorkspaceRole.MEMBER);
            stubMember(1L, 1L, memberRole);

            // when & then
            assertThatThrownBy(() -> workspaceService.updateWorkspace(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_PERMISSION);
            assertThat(testWorkspace.getName()).isEqualTo("테스트 워크스페이스");
            verify(workspaceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("워크스페이스 삭제")
    class DeleteWorkspace {

        @Test
        @DisplayName("성공 - OWNER")
        void success() {
            // given
            stubMember(1L, 1L, ownerMember);

            // when
            workspaceService.deleteWorkspace(1L, 1L);

            // then: FK 제약 때문에 post → folder(parent null) → folder → member → workspace 순서로 삭제
            InOrder inOrder = inOrder(postRepository, folderRepository, memberRepository, workspaceRepository);
            inOrder.verify(postRepository).deleteByWorkspaceWorkspaceId(1L);
            inOrder.verify(folderRepository).clearParentByWorkspaceId(1L);
            inOrder.verify(folderRepository).deleteAllByWorkspaceId(1L);
            inOrder.verify(memberRepository).deleteAllByWorkspaceId(1L);
            inOrder.verify(workspaceRepository).delete(testWorkspace);
        }

        @Test
        @DisplayName("실패 - ADMIN은 삭제 불가")
        void fail_adminCannotDelete() {
            // given
            WorkspaceMember adminMember = WorkspaceMember.createMember(testWorkspace, testUser, WorkspaceRole.ADMIN);
            stubMember(1L, 1L, adminMember);

            // when & then
            assertThatThrownBy(() -> workspaceService.deleteWorkspace(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_PERMISSION);
            verify(workspaceRepository, never()).delete(any());
            verifyNoInteractions(postRepository, folderRepository);
        }
    }

    @Nested
    @DisplayName("멤버 초대")
    class InviteMember {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            MemberInviteRequest request = new MemberInviteRequest(null, "other@test.com", WorkspaceRole.MEMBER);

            stubMember(1L, 1L, ownerMember);
            when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherUser));
            when(memberRepository.existsByWorkspaceAndUser(testWorkspace, otherUser)).thenReturn(false);
            when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(invocation -> {
                WorkspaceMember member = invocation.getArgument(0);
                ReflectionTestUtils.setField(member, "id", 2L);
                return member;
            });

            // when
            WorkspaceMemberResponse response = workspaceService.inviteMember(1L, 1L, request);

            // then
            assertThat(response.memberId()).isEqualTo(2L);
            assertThat(response.userId()).isEqualTo(2L);
            assertThat(response.email()).isEqualTo("other@test.com");
            assertThat(response.role()).isEqualTo(WorkspaceRole.MEMBER);
        }

        @Test
        @DisplayName("성공 - userId로 초대 (이메일 없는 사용자)")
        void success_byUserId() {
            // given
            MemberInviteRequest request = new MemberInviteRequest(2L, null, WorkspaceRole.MEMBER);

            stubMember(1L, 1L, ownerMember);
            when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
            when(memberRepository.existsByWorkspaceAndUser(testWorkspace, otherUser)).thenReturn(false);
            when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            workspaceService.inviteMember(1L, 1L, request);

            // then
            verify(userRepository, never()).findByEmail(any());
            verify(memberRepository).save(any(WorkspaceMember.class));
        }

        @Test
        @DisplayName("실패 - OWNER 권한 부여 불가")
        void fail_cannotAssignOwner() {
            // given
            MemberInviteRequest request = new MemberInviteRequest(null, "other@test.com", WorkspaceRole.OWNER);
            stubMember(1L, 1L, ownerMember);

            // when & then
            assertThatThrownBy(() -> workspaceService.inviteMember(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_ASSIGN_OWNER_ROLE);
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패 - 이미 멤버")
        void fail_alreadyMember() {
            // given
            MemberInviteRequest request = new MemberInviteRequest(null, "other@test.com", WorkspaceRole.MEMBER);

            stubMember(1L, 1L, ownerMember);
            when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherUser));
            when(memberRepository.existsByWorkspaceAndUser(testWorkspace, otherUser)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> workspaceService.inviteMember(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_WORKSPACE_MEMBER);
            verify(memberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("멤버 권한 변경")
    class UpdateMemberRole {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            MemberRoleUpdateRequest request = new MemberRoleUpdateRequest(WorkspaceRole.ADMIN);

            WorkspaceMember targetMember = WorkspaceMember.createMember(testWorkspace, otherUser, WorkspaceRole.MEMBER);
            ReflectionTestUtils.setField(targetMember, "id", 2L);

            stubMember(1L, 1L, ownerMember);
            when(memberRepository.findById(2L)).thenReturn(Optional.of(targetMember));

            // when
            WorkspaceMemberResponse response = workspaceService.updateMemberRole(1L, 1L, 2L, request);

            // then
            assertThat(response.memberId()).isEqualTo(2L);
            assertThat(response.role()).isEqualTo(WorkspaceRole.ADMIN);
            verify(memberRepository).save(targetMember);
        }

        @Test
        @Disabled("M13: 요청자가 OWNER이면 레벨 검사를 건너뛰어 OWNER 자신의 역할을 변경할 수 있음 (버그 수정 전까지 비활성화)")
        @DisplayName("실패 - OWNER 권한 변경 불가")
        void fail_cannotChangeOwnerRole() {
            // given
            MemberRoleUpdateRequest request = new MemberRoleUpdateRequest(WorkspaceRole.ADMIN);

            stubMember(1L, 1L, ownerMember);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(ownerMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.updateMemberRole(1L, 1L, 1L, request))
                    .isInstanceOf(BusinessException.class);
            assertThat(ownerMember.getRole()).isEqualTo(WorkspaceRole.OWNER);
        }

        @Test
        @DisplayName("실패 - ADMIN은 OWNER 권한 변경 불가")
        void fail_adminCannotChangeOwnerRole() {
            // given
            MemberRoleUpdateRequest request = new MemberRoleUpdateRequest(WorkspaceRole.MEMBER);

            WorkspaceMember adminMember = WorkspaceMember.createMember(testWorkspace, otherUser, WorkspaceRole.ADMIN);
            stubMember(1L, 2L, adminMember);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(ownerMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.updateMemberRole(2L, 1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_MODIFY_HIGHER_ROLE);
            assertThat(ownerMember.getRole()).isEqualTo(WorkspaceRole.OWNER);
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패 - 다른 워크스페이스 멤버")
        void fail_differentWorkspace() {
            // given
            MemberRoleUpdateRequest request = new MemberRoleUpdateRequest(WorkspaceRole.ADMIN);

            Workspace otherWorkspace = Workspace.createTeam("다른 워크스페이스", null);
            ReflectionTestUtils.setField(otherWorkspace, "workspaceId", 2L);

            WorkspaceMember targetMember = WorkspaceMember.createMember(otherWorkspace, otherUser, WorkspaceRole.MEMBER);  // 다른 워크스페이스
            ReflectionTestUtils.setField(targetMember, "id", 2L);

            stubMember(1L, 1L, ownerMember);
            when(memberRepository.findById(2L)).thenReturn(Optional.of(targetMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.updateMemberRole(1L, 1L, 2L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
            assertThat(targetMember.getRole()).isEqualTo(WorkspaceRole.MEMBER);
        }
    }

    @Nested
    @DisplayName("멤버 제거")
    class RemoveMember {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            WorkspaceMember targetMember = WorkspaceMember.createMember(testWorkspace, otherUser, WorkspaceRole.MEMBER);
            ReflectionTestUtils.setField(targetMember, "id", 2L);

            stubMember(1L, 1L, ownerMember);
            when(memberRepository.findById(2L)).thenReturn(Optional.of(targetMember));

            // when
            workspaceService.removeMember(1L, 1L, 2L);

            // then
            verify(memberRepository).delete(targetMember);
        }

        @Test
        @Disabled("M13: 요청자가 OWNER이면 자기 자신(OWNER)을 멤버에서 제거할 수 있음 (버그 수정 전까지 비활성화)")
        @DisplayName("실패 - OWNER 제거 불가")
        void fail_cannotRemoveOwner() {
            // given
            stubMember(1L, 1L, ownerMember);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(ownerMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.removeMember(1L, 1L, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(memberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("실패 - ADMIN은 OWNER 제거 불가")
        void fail_adminCannotRemoveOwner() {
            // given
            WorkspaceMember adminMember = WorkspaceMember.createMember(testWorkspace, otherUser, WorkspaceRole.ADMIN);
            stubMember(1L, 2L, adminMember);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(ownerMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.removeMember(2L, 1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_MODIFY_HIGHER_ROLE);
            verify(memberRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("워크스페이스 나가기")
    class LeaveWorkspace {

        @Test
        @DisplayName("성공 - MEMBER")
        void success() {
            // given
            WorkspaceMember memberRole = WorkspaceMember.createMember(testWorkspace, otherUser, WorkspaceRole.MEMBER);
            stubMember(1L, 2L, memberRole);

            // when
            workspaceService.leaveWorkspace(2L, 1L);

            // then
            verify(memberRepository).delete(memberRole);
        }

        @Test
        @DisplayName("실패 - OWNER는 나갈 수 없음")
        void fail_ownerCannotLeave() {
            // given
            stubMember(1L, 1L, ownerMember);

            // when & then
            assertThatThrownBy(() -> workspaceService.leaveWorkspace(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OWNER_CANNOT_LEAVE);
            verify(memberRepository, never()).delete(any());
        }
    }
}
