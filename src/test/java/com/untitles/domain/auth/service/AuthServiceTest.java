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

    @Test
    @DisplayName("이메일 없이 가입하면 이메일은 null로 저장하고 이메일 중복 검사를 건너뛴다")
    void signupWithoutEmail() {
        ReflectionTestUtils.setField(authService, "emailVerificationRequired", false);
        UserCreateRequestDTO noEmail = UserCreateRequestDTO.builder()
                .email("").loginId("tester").password("abcd1234!").nickname("테스터").build();
        when(passwordEncoder.encode("abcd1234!")).thenReturn("hashed");
        when(userRepository.save(any(Users.class))).thenAnswer(inv -> {
            Users user = inv.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 1L);
            return user;
        });

        authService.signup(noEmail);

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository).save(argThat(user -> user.getEmail() == null));
        verify(emailService, never()).deleteVerification(any());
    }

    @Test
    @DisplayName("이메일 인증이 필수인데 이메일이 없으면 가입할 수 없다")
    void verificationRequiredWithoutEmail() {
        ReflectionTestUtils.setField(authService, "emailVerificationRequired", true);
        UserCreateRequestDTO noEmail = UserCreateRequestDTO.builder()
                .loginId("tester").password("abcd1234!").nickname("테스터").build();

        assertThatThrownBy(() -> authService.signup(noEmail))
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("유효한 리프레시 토큰이면 새 토큰을 발급한다")
    void refresh() {
        Users user = Users.builder().userId(1L).loginId("tester").nickname("테스터").build();
        when(jwtProvider.validateRefreshToken("rt")).thenReturn(true);
        when(jwtProvider.getUserId("rt")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(jwtProvider.createAccessToken(1L, "tester")).thenReturn("new-at");
        when(jwtProvider.createRefreshToken(1L, "tester")).thenReturn("new-rt");

        var tokens = authService.refresh("rt");

        assertThat(tokens.accessToken()).isEqualTo("new-at");
        assertThat(tokens.refreshToken()).isEqualTo("new-rt");
    }

    @Test
    @DisplayName("리프레시 토큰이 아니거나 만료되면 401")
    void refreshInvalid() {
        when(jwtProvider.validateRefreshToken("at")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("at"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 리프레시 토큰은 거부한다")
    void refreshDeletedUser() {
        when(jwtProvider.validateRefreshToken("rt")).thenReturn(true);
        when(jwtProvider.getUserId("rt")).thenReturn(9L);
        when(userRepository.findById(9L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.refresh("rt"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
