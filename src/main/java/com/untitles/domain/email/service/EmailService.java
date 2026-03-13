package com.untitles.domain.email.service;

import com.untitles.domain.email.entity.EmailVerification;
import com.untitles.domain.email.repository.EmailVerificationRepository;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sesv2.model.*;

import java.util.Random;

// domain/email/service/EmailService.java
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailService {

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

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
    @Transactional
    public void sendVerificationCode(String email) {
        // 이미 가입된 이메일인지 확인- 부하테스트용 임시 주석
//         if (userRepository.existsByEmail(email)) {
//             throw new BusinessException(ErrorCode.ALREADY_REGISTERED_EMAIL);
//         }
//
//        // 기존 인증 데이터 삭제
//        verificationRepository.deleteByEmail(email);

        // 6자리 인증번호 생성
        String verificationCode = String.format("%06d", new Random().nextInt(1000000));

        // DB 저장
        EmailVerification verification = EmailVerification.create(email, verificationCode, EXPIRATION_MINUTES);
        verificationRepository.save(verification);

        // 이메일 발송
        emailSender.sendAsync(email, verificationCode);

        log.info("인증번호 발송 완료: {}", email);
    }

    /**
     * 인증번호 확인
     */
    @Transactional
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
    @Transactional
    public void deleteVerification(String email) {
        verificationRepository.deleteByEmail(email);
    }


}