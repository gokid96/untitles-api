package com.untitles.domain.auth.service;

import com.untitles.domain.auth.repository.PasswordResetRepository;
import com.untitles.domain.auth.repository.RefreshTokenRepository;
import com.untitles.domain.email.service.EmailSender;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.jwt.JwtProvider;
import com.untitles.support.H2JpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@H2JpaTest
class PasswordResetServiceTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordResetRepository passwordResetRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private EmailSender emailSender;
    private RefreshTokenService refreshTokenService;
    private PasswordResetService service;
    private Users user;

    @BeforeEach
    void setUp() {
        emailSender = mock(EmailSender.class);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository,
                new JwtProvider("0123456789abcdef0123456789abcdef0123456789abcdef", 60_000, 600_000));
        service = new PasswordResetService(userRepository, passwordResetRepository, encoder, emailSender, refreshTokenService);
        user = userRepository.save(Users.createLocal("me@example.com", "me", encoder.encode("old-pass"), "나"));
    }

    private String requestCode() {
        service.request("me@example.com");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendPasswordResetAsync(eq("me@example.com"), code.capture());
        return code.getValue();
    }

    @Test
    @DisplayName("메일로 받은 코드로 비밀번호를 바꾸면 기존 로그인은 모두 끊긴다")
    void resetsPassword() {
        String oldRefresh = refreshTokenService.issue(user.getUserId(), "me").refreshToken();
        String code = requestCode();

        service.confirm("me@example.com", code, "new-pass");

        assertThat(encoder.matches("new-pass", userRepository.findById(user.getUserId()).orElseThrow().getPassword())).isTrue();
        assertThatThrownBy(() -> refreshTokenService.consume(oldRefresh))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        // 코드는 한 번만 쓸 수 있다
        assertThatThrownBy(() -> service.confirm("me@example.com", code, "again"))
                .extracting("errorCode").isEqualTo(ErrorCode.PASSWORD_RESET_INVALID);
    }

    @Test
    @DisplayName("등록되지 않은 이메일이어도 같은 응답이고 메일은 보내지 않는다")
    void unknownEmailIsSilent() {
        service.request("nobody@example.com");

        verify(emailSender, never()).sendPasswordResetAsync(anyString(), anyString());
    }

    @Test
    @DisplayName("틀린 코드를 5번 넘게 입력하면 그 요청은 더 이상 쓸 수 없다")
    void attemptLimit() {
        String code = requestCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.confirm("me@example.com", wrong, "new-pass"))
                    .extracting("errorCode").isEqualTo(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        assertThatThrownBy(() -> service.confirm("me@example.com", code, "new-pass"))
                .extracting("errorCode").isEqualTo(ErrorCode.PASSWORD_RESET_INVALID);
    }
}
