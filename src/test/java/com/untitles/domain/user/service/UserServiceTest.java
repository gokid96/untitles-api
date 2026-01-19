package com.untitles.domain.user.service;

import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("회원가입 성공")
    void createUser() {
    }

    @Test
    @DisplayName("중복 실패")
    void duplicateEmail() {
        // given
        UserCreateRequestDTO request = UserCreateRequestDTO.builder()
                .email("dup@test.com")
                .loginId("testuser")
                .password("password123")
                .nickname("테스터")
                .build();

        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용중인 이메일입니다.");

        verify(userRepository, never()).save(any());
    }
}