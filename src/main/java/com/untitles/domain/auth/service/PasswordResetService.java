package com.untitles.domain.auth.service;

import com.untitles.domain.auth.entity.PasswordReset;
import com.untitles.domain.auth.repository.PasswordResetRepository;
import com.untitles.domain.email.service.EmailSender;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 이메일 인증 코드로 비밀번호 재설정. 이메일을 등록한 계정만 가능하다.
 * 가입 여부가 드러나지 않도록 요청은 항상 같은 응답을 준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int EXPIRATION_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void request(String email) {
        Optional<Users> user = userRepository.findByEmail(email.trim());
        if (user.isEmpty()) {
            log.info("비밀번호 재설정 요청: 등록되지 않은 이메일");
            return;
        }
        Long userId = user.get().getUserId();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        passwordResetRepository.deleteByUserId(userId);
        passwordResetRepository.save(PasswordReset.of(userId, RefreshTokenService.hash(code),
                LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES)));
        emailSender.sendPasswordResetAsync(user.get().getEmail(), code);
    }

    @Transactional(noRollbackFor = BusinessException.class)  // 실패 횟수 증가는 예외가 나도 저장
    public void confirm(String email, String code, String newPassword) {
        Users user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_INVALID));
        PasswordReset reset = passwordResetRepository.findByUserId(user.getUserId())
                .filter(r -> r.isUsable(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_INVALID));

        if (!reset.getCodeHash().equals(RefreshTokenService.hash(code))) {
            reset.increaseAttempt();
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
        passwordResetRepository.delete(reset);
        refreshTokenService.revokeAll(user.getUserId());  // 다른 기기의 로그인도 끊는다
        log.info("비밀번호 재설정 완료: userId={}", user.getUserId());
    }
}
