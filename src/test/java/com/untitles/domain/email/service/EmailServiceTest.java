package com.untitles.domain.email.service;

import com.untitles.domain.email.entity.EmailVerification;
import com.untitles.domain.email.repository.EmailVerificationRepository;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private EmailVerificationRepository verificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    @DisplayName("이미 가입된 이메일로는 인증번호를 보내지 않는다")
    void sendToRegisteredEmail() {
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> emailService.sendVerificationCode(EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_REGISTERED_EMAIL);

        verify(verificationRepository, never()).save(any());
        verify(emailSender, never()).sendAsync(anyString(), anyString());
    }

    @Test
    @DisplayName("재발송하면 기존 인증 데이터를 지우고 새로 저장한다")
    void resendReplacesPreviousCode() {
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);

        emailService.sendVerificationCode(EMAIL);

        InOrder inOrder = inOrder(verificationRepository, emailSender);
        inOrder.verify(verificationRepository).deleteByEmail(EMAIL);
        inOrder.verify(verificationRepository).save(any(EmailVerification.class));
        inOrder.verify(emailSender).sendAsync(eq(EMAIL), anyString());
    }

    @Test
    @DisplayName("인증번호는 항상 6자리 숫자다")
    void codeIsSixDigits() {
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);

        emailService.sendVerificationCode(EMAIL);

        verify(emailSender).sendAsync(eq(EMAIL), argThat(code -> code.matches("\\d{6}")));
    }

    @Test
    @DisplayName("틀리면 시도 횟수가 늘고, 5회 틀리면 맞는 코드도 거부한다")
    void attemptLimit() {
        EmailVerification verification = EmailVerification.create(EMAIL, "123456", 10);
        when(verificationRepository.findByEmailAndVerifiedFalse(EMAIL)).thenReturn(Optional.of(verification));
        stubAttemptCounter(verification);

        for (int i = 0; i < EmailVerification.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> emailService.verifyCode(EMAIL, "000000"))
                    .extracting("errorCode").isEqualTo(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }
        assertThat(verification.getAttemptCount()).isEqualTo(EmailVerification.MAX_ATTEMPTS);

        assertThatThrownBy(() -> emailService.verifyCode(EMAIL, "123456"))
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        assertThat(verification.isVerified()).isFalse();
    }

    @Test
    @DisplayName("한도 안에서는 맞는 코드로 인증된다")
    void verifyAfterMistake() {
        EmailVerification verification = EmailVerification.create(EMAIL, "123456", 10);
        when(verificationRepository.findByEmailAndVerifiedFalse(EMAIL)).thenReturn(Optional.of(verification));
        stubAttemptCounter(verification);

        assertThatThrownBy(() -> emailService.verifyCode(EMAIL, "000000"))
                .isInstanceOf(BusinessException.class);

        assertThat(emailService.verifyCode(EMAIL, "123456")).isTrue();
        assertThat(verification.isVerified()).isTrue();
    }

    @Test
    @DisplayName("인증 실패 예외가 나도 시도 횟수 증가는 롤백되지 않는다")
    void attemptCountNotRolledBack() throws Exception {
        Method method = EmailService.class.getMethod("verifyCode", String.class, String.class);
        Transactional tx = method.getAnnotation(Transactional.class);

        assertThat(tx.noRollbackFor()).contains(BusinessException.class);
    }

    /** DB의 원자적 증가(useAttempt)를 엔티티 카운터로 흉내 낸다 */
    private void stubAttemptCounter(EmailVerification verification) {
        when(verificationRepository.useAttempt(any(), anyInt())).thenAnswer(inv -> {
            if (verification.getAttemptCount() >= EmailVerification.MAX_ATTEMPTS) {
                return 0;
            }
            verification.increaseAttempt();
            return 1;
        });
    }
}
