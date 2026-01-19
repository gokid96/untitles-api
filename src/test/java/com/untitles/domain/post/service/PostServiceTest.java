package com.untitles.domain.post.service;

import com.untitles.domain.post.dto.request.PostCreateRequestDTO;
import com.untitles.domain.post.dto.response.PostResponseDTO;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;


    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("게시글 작성 성공")
    void createPost() {
        // given
        Long userId = 1L;
        Users user = Users.builder().userId(userId).loginId("테스터").build();

        PostCreateRequestDTO request = PostCreateRequestDTO.builder()
                .title("테스트 게시물")
                .content("테스트 내용")
                .build();

        Post savedPost = Post.builder()
                .postId(1L)
                .title("테스트 게시물")
                .author(user)
                .build();

        // when
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        // then
        PostResponseDTO response = postService.createPost(userId, request);

        assertThat(response.getTitle()).isEqualTo("테스트 게시물");
    }
}