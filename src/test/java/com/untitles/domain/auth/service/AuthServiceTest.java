package com.untitles.domain.auth.service;

import com.untitles.domain.email.service.EmailService;
import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    private final UserCreateRequestDTO request = UserCreateRequestDTO.builder()
            .email("user@example.com").loginId("tester").password("abcd1234!").nickname("테스터")
            .build();

    @Test
    @DisplayName("이메일 인증이 필수면 인증하지 않은 이메일로 가입할 수 없다")
    void verificationRequired() {
        ReflectionTestUtils.setField(authService, "emailVerificationRequired", true);
        when(emailService.isVerified("user@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("이메일 인증을 끄면 인증 없이 가입된다")
    void verificationDisabled() {
        ReflectionTestUtils.setField(authService, "emailVerificationRequired", false);
        when(passwordEncoder.encode("abcd1234!")).thenReturn("hashed");
        when(userRepository.save(any(Users.class))).thenAnswer(inv -> {
            Users user = inv.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 1L);
            return user;
        });

        assertThat(authService.signup(request)).isNotNull();

        verify(emailService, never()).isVerified(any());
        verify(userRepository).save(any(Users.class));
    }

    @Test
    @DisplayName("이메일 인증을 꺼도 중복 이메일은 막는다")
    void duplicateEmailStillBlocked() {
        ReflectionTestUtils.setField(authService, "emailVerificationRequired", false);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }
}
