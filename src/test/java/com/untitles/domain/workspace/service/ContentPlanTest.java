package com.untitles.domain.workspace.service;

import com.untitles.domain.folder.service.FolderService;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.dto.request.FolderCreateRequestDTO;
import com.untitles.domain.post.service.PostService;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.dto.request.PostCreateRequestDTO;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.entity.UserPlan;
import com.untitles.domain.workspace.entity.*;
import com.untitles.domain.workspace.repository.*;
import com.untitles.global.util.*;
import com.untitles.global.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ContentPlanTest {
    @Mock WorkspaceMemberRepository members;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock FolderRepository folderRepository;
    @Mock PostRepository postRepository;
    @Mock HtmlSanitizer htmlSanitizer;
    @Mock com.untitles.domain.link.service.PostLinkService postLinkService;
    @Mock WorkspaceMemberHelper workspaceMemberHelper;
    @InjectMocks FolderService folders;
    @InjectMocks PostService posts;

    private WorkspaceMember member() {
        // PRO requester is not sufficient: services must consult the owner entitlement.
        return WorkspaceMember.createMember(Workspace.createTeam("team", null),
                Users.builder().userId(10L).nickname("member").plan(UserPlan.PRO).build(), WorkspaceRole.MEMBER);
    }

    @Test void newUsersAreFree() {
        assertThat(Users.builder().nickname("new").build().getPlan()).isEqualTo(UserPlan.FREE);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void entitlementUsesOwnerPlan(boolean proOwner) {
        when(members.existsByWorkspaceWorkspaceIdAndRoleAndUserPlan(1L, WorkspaceRole.OWNER, UserPlan.PRO))
                .thenReturn(proOwner);
        assertThat(new WorkspaceMemberHelper(members).hasUnlimitedContent(1L)).isEqualTo(proOwner);
        verify(members).existsByWorkspaceWorkspaceIdAndRoleAndUserPlan(1L, WorkspaceRole.OWNER, UserPlan.PRO);
    }

    @Test void viewerCannotCreateEvenInProWorkspace() {
        WorkspaceMember viewer = WorkspaceMember.createMember(Workspace.createPersonal(),
                Users.builder().nickname("viewer").build(), WorkspaceRole.VIEWER);
        when(workspaceMemberHelper.getMemberOrThrow(10L, 1L)).thenReturn(viewer);
        doThrow(new BusinessException(ErrorCode.WRITE_PERMISSION_DENIED))
                .when(workspaceMemberHelper).checkWritePermission(viewer);
        assertThatThrownBy(() -> folders.createFolder(10L, 1L, new FolderCreateRequestDTO()))
                .extracting("errorCode").isEqualTo(ErrorCode.WRITE_PERMISSION_DENIED);
        assertThatThrownBy(() -> posts.createPost(10L, 1L,
                PostCreateRequestDTO.builder().title("note").content("text").build()))
                .extracting("errorCode").isEqualTo(ErrorCode.WRITE_PERMISSION_DENIED);
        verifyNoInteractions(folderRepository, postRepository);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void folderLimitUsesOwnerEntitlement(boolean proOwner) {
        WorkspaceMember member = member();
        when(workspaceMemberHelper.getMemberOrThrow(10L, 1L)).thenReturn(member);
        when(folderRepository.countByWorkspace(member.getWorkspace())).thenReturn(20L);
        when(workspaceMemberHelper.hasUnlimitedContent(1L)).thenReturn(proOwner);
        FolderCreateRequestDTO request = new FolderCreateRequestDTO();
        if (proOwner) {
            when(folderRepository.save(any(Folder.class))).thenAnswer(i -> i.getArgument(0));
            assertThatCode(() -> folders.createFolder(10L, 1L, request)).doesNotThrowAnyException();
            verify(folderRepository).save(any());
        } else {
            assertThatThrownBy(() -> folders.createFolder(10L, 1L, request))
                    .extracting("errorCode").isEqualTo(ErrorCode.FOLDER_LIMIT_EXCEEDED);
            verify(folderRepository, never()).save(any());
        }
        verify(workspaceMemberHelper).checkWritePermission(member);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void postLimitUsesOwnerEntitlement(boolean proOwner) {
        WorkspaceMember member = member();
        when(workspaceMemberHelper.getMemberOrThrow(10L, 1L)).thenReturn(member);
        when(postRepository.countByWorkspace(member.getWorkspace())).thenReturn(50L);
        when(workspaceMemberHelper.hasUnlimitedContent(1L)).thenReturn(proOwner);
        PostCreateRequestDTO request = PostCreateRequestDTO.builder().title("note").content("text").build();
        if (proOwner) {
            when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));
            assertThatCode(() -> posts.createPost(10L, 1L, request)).doesNotThrowAnyException();
            verify(postRepository).save(any());
        } else {
            assertThatThrownBy(() -> posts.createPost(10L, 1L, request))
                    .extracting("errorCode").isEqualTo(ErrorCode.POST_LIMIT_EXCEEDED);
            verify(postRepository, never()).save(any());
        }
        verify(workspaceMemberHelper).checkWritePermission(member);
    }
}
