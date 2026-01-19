package com.untitles;

import com.untitles.domain.post.dto.request.PostCreateRequestDTO;
import com.untitles.domain.post.dto.response.PostResponseDTO;
import com.untitles.domain.post.service.PostService;
import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.dto.response.UserResponseDTO;
import com.untitles.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserPostIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private PostService postService;

    @Test
    @DisplayName("회원가입-게시글작성-수정 통합 테스트")
    void userPostIntegrationTest() {
        // 1. 회원가입
        UserCreateRequestDTO userRequest = UserCreateRequestDTO.builder()
                .email("test@example.com")
                .loginId("testuser")
                .password("Test123!")
                .nickname("테스터")
                .build();

        UserResponseDTO userResponse = userService.createUser(userRequest);

        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getLoginId()).isEqualTo("testuser");
        assertThat(userResponse.getNickname()).isEqualTo("테스터");

        // 2. 게시글 작성
        PostCreateRequestDTO createPostRequest = PostCreateRequestDTO.builder()
                .title("테스트 게시글")
                .content("테스트 본문입니다.")
                .summary("테스트 요약")
                .build();

        PostResponseDTO createdPost = postService.createPost(
                userResponse.getUserId(),
                createPostRequest
        );

        assertThat(createdPost).isNotNull();
        assertThat(createdPost.getTitle()).isEqualTo("테스트 게시글");
      //  assertThat(createdPost.getAuthor().getNickname()).isEqualTo("테스터");

        // 3. 게시글 수정
        PostCreateRequestDTO updatePostRequest = PostCreateRequestDTO.builder()
                .title("수정된 게시글")
                .content("수정된 본문입니다.")
                .summary("수정된 요약")
                .build();

        PostResponseDTO updatedPost = postService.updatePost(
                userResponse.getUserId(),
                createdPost.getPostId(),
                updatePostRequest
        );

        assertThat(updatedPost).isNotNull();
        //assertThat(updatedPost.getTitle()).isEqualTo("수정된 게시글");
        assertThat(updatedPost.getContent()).isEqualTo("수정된 본문입니다.");
       // assertThat(updatedPost.getAuthor().getNickname()).isEqualTo("테스터");
    }
}
