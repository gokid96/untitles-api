package com.untitles.domain.workspace.service;

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
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private WorkspaceService workspaceService;

    private Users testUser;
    private Users otherUser;
    private Workspace testWorkspace;
    private WorkspaceMember ownerMember;

    @BeforeEach
    void setUp() {
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

        // 테스트 워크스페이스 생성
        testWorkspace = Workspace.builder()
                .name("테스트 워크스페이스")
                .description("테스트용")
                .build();
        ReflectionTestUtils.setField(testWorkspace, "workspaceId", 1L);

        // OWNER 멤버
        ownerMember = WorkspaceMember.builder()
                .workspace(testWorkspace)
                .user(testUser)
                .role(WorkspaceRole.OWNER)
                .build();
        ReflectionTestUtils.setField(ownerMember, "id", 1L);
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
                ReflectionTestUtils.setField(ws, "workspaceId", 1L);
                return ws;
            });
            when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            WorkspaceResponse response = workspaceService.createWorkspace(1L, request);

            // then
            assertThat(response.name()).isEqualTo("새 워크스페이스");
            assertThat(response.myRole()).isEqualTo(WorkspaceRole.OWNER);
            verify(workspaceRepository).save(any(Workspace.class));
            verify(memberRepository).save(any(WorkspaceMember.class));
        }

        @Test
        @DisplayName("실패 - 사용자 없음")
        void fail_userNotFound() {
            // given
            WorkspaceCreateRequest request = new WorkspaceCreateRequest("새 워크스페이스", "설명");
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workspaceService.createWorkspace(999L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("사용자를 찾을 수 없습니다.");
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
            when(memberRepository.findAllByUser(testUser)).thenReturn(List.of(ownerMember));

            // when
            List<WorkspaceResponse> responses = workspaceService.getMyWorkspaces(1L);

            // then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).name()).isEqualTo("테스트 워크스페이스");
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

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));

            // when
            WorkspaceResponse response = workspaceService.updateWorkspace(1L, 1L, request);

            // then
            assertThat(response.name()).isEqualTo("수정된 이름");
        }

        @Test
        @DisplayName("실패 - 권한 없음 (MEMBER)")
        void fail_noPermission() {
            // given
            WorkspaceUpdateRequest request = new WorkspaceUpdateRequest("수정된 이름", null);

            WorkspaceMember memberRole = WorkspaceMember.builder()
                    .workspace(testWorkspace)
                    .user(testUser)
                    .role(WorkspaceRole.MEMBER)
                    .build();

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(memberRole));

            // when & then
            assertThatThrownBy(() -> workspaceService.updateWorkspace(1L, 1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("권한이 부족합니다.");
        }
    }

    @Nested
    @DisplayName("워크스페이스 삭제")
    class DeleteWorkspace {

        @Test
        @DisplayName("성공 - OWNER")
        void success() {
            // given
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));

            // when
            workspaceService.deleteWorkspace(1L, 1L);

            // then
            verify(workspaceRepository).delete(testWorkspace);
        }

        @Test
        @DisplayName("실패 - ADMIN은 삭제 불가")
        void fail_adminCannotDelete() {
            // given
            WorkspaceMember adminMember = WorkspaceMember.builder()
                    .workspace(testWorkspace)
                    .user(testUser)
                    .role(WorkspaceRole.ADMIN)
                    .build();

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(adminMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.deleteWorkspace(1L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("권한이 부족합니다.");
        }
    }

    @Nested
    @DisplayName("멤버 초대")
    class InviteMember {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            MemberInviteRequest request = new MemberInviteRequest("other@test.com", WorkspaceRole.MEMBER);

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));
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
            assertThat(response.email()).isEqualTo("other@test.com");
            assertThat(response.role()).isEqualTo(WorkspaceRole.MEMBER);
        }

        @Test
        @DisplayName("실패 - OWNER 권한 부여 불가")
        void fail_cannotAssignOwner() {
            // given
            MemberInviteRequest request = new MemberInviteRequest("other@test.com", WorkspaceRole.OWNER);

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.inviteMember(1L, 1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OWNER 권한은 부여할 수 없습니다.");
        }

        @Test
        @DisplayName("실패 - 이미 멤버")
        void fail_alreadyMember() {
            // given
            MemberInviteRequest request = new MemberInviteRequest("other@test.com", WorkspaceRole.MEMBER);

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));
            when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherUser));
            when(memberRepository.existsByWorkspaceAndUser(testWorkspace, otherUser)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> workspaceService.inviteMember(1L, 1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("이미 워크스페이스 멤버입니다.");
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

            WorkspaceMember targetMember = WorkspaceMember.builder()
                    .workspace(testWorkspace)
                    .user(otherUser)
                    .role(WorkspaceRole.MEMBER)
                    .build();
            ReflectionTestUtils.setField(targetMember, "id", 2L);

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));
            when(memberRepository.findById(2L)).thenReturn(Optional.of(targetMember));

            // when
            WorkspaceMemberResponse response = workspaceService.updateMemberRole(1L, 1L, 2L, request);

            // then
            assertThat(response.role()).isEqualTo(WorkspaceRole.ADMIN);
        }

        @Test
        @DisplayName("실패 - OWNER 권한 변경 불가")
        void fail_cannotChangeOwnerRole() {
            // given
            MemberRoleUpdateRequest request = new MemberRoleUpdateRequest(WorkspaceRole.ADMIN);

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));
            when(memberRepository.findById(1L)).thenReturn(Optional.of(ownerMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.updateMemberRole(1L, 1L, 1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OWNER의 권한은 변경할 수 없습니다.");
        }

        @Test
        @DisplayName("실패 - 다른 워크스페이스 멤버")
        void fail_differentWorkspace() {
            // given
            MemberRoleUpdateRequest request = new MemberRoleUpdateRequest(WorkspaceRole.ADMIN);

            Workspace otherWorkspace = Workspace.builder()
                    .name("다른 워크스페이스")
                    .build();
            ReflectionTestUtils.setField(otherWorkspace, "workspaceId", 2L);

            WorkspaceMember targetMember = WorkspaceMember.builder()
                    .workspace(otherWorkspace)  // 다른 워크스페이스
                    .user(otherUser)
                    .role(WorkspaceRole.MEMBER)
                    .build();
            ReflectionTestUtils.setField(targetMember, "id", 2L);

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));
            when(memberRepository.findById(2L)).thenReturn(Optional.of(targetMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.updateMemberRole(1L, 1L, 2L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("해당 워크스페이스의 멤버가 아닙니다.");
        }
    }

    @Nested
    @DisplayName("멤버 제거")
    class RemoveMember {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            WorkspaceMember targetMember = WorkspaceMember.builder()
                    .workspace(testWorkspace)
                    .user(otherUser)
                    .role(WorkspaceRole.MEMBER)
                    .build();
            ReflectionTestUtils.setField(targetMember, "id", 2L);

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));
            when(memberRepository.findById(2L)).thenReturn(Optional.of(targetMember));

            // when
            workspaceService.removeMember(1L, 1L, 2L);

            // then
            verify(memberRepository).delete(targetMember);
        }

        @Test
        @DisplayName("실패 - OWNER 제거 불가")
        void fail_cannotRemoveOwner() {
            // given
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));
            when(memberRepository.findById(1L)).thenReturn(Optional.of(ownerMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.removeMember(1L, 1L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OWNER는 제거할 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("워크스페이스 나가기")
    class LeaveWorkspace {

        @Test
        @DisplayName("성공 - MEMBER")
        void success() {
            // given
            WorkspaceMember memberRole = WorkspaceMember.builder()
                    .workspace(testWorkspace)
                    .user(otherUser)
                    .role(WorkspaceRole.MEMBER)
                    .build();

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, otherUser))
                    .thenReturn(Optional.of(memberRole));

            // when
            workspaceService.leaveWorkspace(2L, 1L);

            // then
            verify(memberRepository).delete(memberRole);
        }

        @Test
        @DisplayName("실패 - OWNER는 나갈 수 없음")
        void fail_ownerCannotLeave() {
            // given
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(memberRepository.findByWorkspaceAndUser(testWorkspace, testUser))
                    .thenReturn(Optional.of(ownerMember));

            // when & then
            assertThatThrownBy(() -> workspaceService.leaveWorkspace(1L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OWNER는 워크스페이스를 나갈 수 없습니다. 삭제하거나 양도하세요.");
        }
    }
}
