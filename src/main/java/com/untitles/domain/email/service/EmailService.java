package com.untitles.domain.email.service;

import com.untitles.domain.email.entity.EmailVerification;
import com.untitles.domain.email.repository.EmailVerificationRepository;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

import java.util.Random;

// domain/email/service/EmailService.java
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final SesV2Client sesV2Client;
    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;

    @Value("${aws.ses.from-email}")
    private String fromEmail;

    private static final int EXPIRATION_MINUTES = 10;

    /**
     * 이메일 중복 확인
     */
    public boolean checkDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 인증번호 발송
     */
    public void sendVerificationCode(String email) {
        // 이미 가입된 이메일인지 확인
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED_EMAIL);
        }

        // 기존 인증 데이터 삭제
        verificationRepository.deleteByEmail(email);

        // 6자리 인증번호 생성
        String code = String.format("%06d", new Random().nextInt(1000000));

        // DB 저장
        EmailVerification verification = EmailVerification.create(email, code, EXPIRATION_MINUTES);
        verificationRepository.save(verification);

        // 이메일 발송
        sendEmail(email, code);

        log.info("인증번호 발송 완료: {}", email);
    }

    /**
     * 인증번호 확인
     */
    public boolean verifyCode(String email, String code) {
        EmailVerification verification = verificationRepository
                .findByEmailAndVerifiedFalse(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_REQUESTED));

        // 만료 체크
        if (verification.isExpired()) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }

        // 코드 일치 확인
        if (!verification.getCode().equals(code)) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        // 인증 완료 처리
        verification.verify();
        verificationRepository.save(verification);

        log.info("이메일 인증 완료: {}", email);
        return true;
    }

    /**
     * 인증 완료 여부 확인
     */
    public boolean isVerified(String email) {
        return verificationRepository
                .findByEmailAndVerifiedTrue(email)
                .filter(v -> !v.isExpired())
                .isPresent();
    }

    /**
     * 인증 데이터 삭제 (회원가입 완료 후)
     */
    public void deleteVerification(String email) {
        verificationRepository.deleteByEmail(email);
    }

    /**
     * 이메일 발송 (SES)
     */
    private void sendEmail(String toEmail, String verificationCode) {
        String subject = "[untitles] 회원가입 인증 코드";
        String htmlBody = String.format("""
            <html>
            <body>
                <h2>이메일 인증</h2>
                <p>아래 인증 코드를 입력해주세요:</p>
                <h1 style="color: #4A90D9;">%s</h1>
                <p>이 코드는 %d분간 유효합니다.</p>
            </body>
            </html>
            """, verificationCode, EXPIRATION_MINUTES);

        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(fromEmail)
                .destination(Destination.builder()
                        .toAddresses(toEmail)
                        .build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder()
                                        .data(subject)
                                        .charset("UTF-8")
                                        .build())
                                .body(Body.builder()
                                        .html(Content.builder()
                                                .data(htmlBody)
                                                .charset("UTF-8")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        sesV2Client.sendEmail(request);
    }
}