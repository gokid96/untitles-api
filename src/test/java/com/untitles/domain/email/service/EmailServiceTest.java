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
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> emailService.sendVerificationCode(EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_REGISTERED_EMAIL);

        verify(verificationRepository, never()).save(any());
        verify(emailSender, never()).sendAsync(anyString(), anyString());
    }

    @Test
    @DisplayName("재발송하면 기존 인증 데이터를 지우고 새로 저장한다")
    void resendReplacesPreviousCode() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        emailService.sendVerificationCode(EMAIL);

        InOrder inOrder = inOrder(verificationRepository, emailSender);
        inOrder.verify(verificationRepository).deleteByEmail(EMAIL);
        inOrder.verify(verificationRepository).save(any(EmailVerification.class));
        inOrder.verify(emailSender).sendAsync(eq(EMAIL), anyString());
    }
}
