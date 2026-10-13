package com.untitles.domain.post.service;

import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.dto.request.PostCreateRequestDTO;
import com.untitles.domain.post.dto.response.PostResponseDTO;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.global.util.HtmlSanitizer;
import com.untitles.global.util.WorkspaceMemberHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private HtmlSanitizer htmlSanitizer;
    @Mock
    private WorkspaceMemberHelper workspaceMemberHelper;

    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("게시글 작성 성공")
    void createPost() {
        // given
        Long userId = 1L;
        Long workspaceId = 1L;
        Users user = Users.builder().userId(userId).loginId("tester").nickname("테스터").build();

        Workspace workspace = Workspace.createTeam("테스트 워크스페이스", null);
        ReflectionTestUtils.setField(workspace, "workspaceId", workspaceId);
        WorkspaceMember member = WorkspaceMember.createOwner(workspace, user);

        String rawContent = "<p>테스트 내용</p><script>alert(1)</script>";
        String sanitizedContent = "<p>테스트 내용</p>";
        PostCreateRequestDTO request = PostCreateRequestDTO.builder()
                .title("테스트 게시물")
                .content(rawContent)
                .build();

        when(workspaceMemberHelper.getMemberOrThrow(userId, workspaceId)).thenReturn(member);
        when(htmlSanitizer.sanitize(rawContent)).thenReturn(sanitizedContent);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "postId", 1L);
            return post;
        });

        // when
        PostResponseDTO response = postService.createPost(userId, workspaceId, request);

        // then
        assertThat(response.getPostId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("테스트 게시물");
        assertThat(response.getContent()).isEqualTo(sanitizedContent);
        assertThat(response.getAuthorId()).isEqualTo(userId);
        assertThat(response.getAuthNickname()).isEqualTo("테스터");
        assertThat(response.getFolderId()).isNull();

        verify(workspaceMemberHelper).checkWritePermission(member);
        verify(htmlSanitizer).sanitize(rawContent);
        verifyNoInteractions(folderRepository);
    }
}