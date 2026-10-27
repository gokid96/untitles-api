package com.untitles.domain.auth.service;

import com.untitles.domain.auth.dto.response.LoginResponse;
import com.untitles.domain.auth.dto.response.TokenResponse;
import com.untitles.domain.auth.social.PendingLinkStore;
import com.untitles.domain.auth.social.SocialLoginService;
import com.untitles.domain.email.service.EmailService;
import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.dto.request.UserLoginRequestDTO;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AccountSetupService accountSetupService;
    private final RefreshTokenService refreshTokenService;
    private final SocialLoginService socialLoginService;
    private final PendingLinkStore pendingLinkStore;

    @Value("${app.signup.email-verification-required:true}")
    private boolean emailVerificationRequired;

    private static final String LOGIN_FAILED = "이메일(아이디) 또는 비밀번호가 올바르지 않습니다.";

    /**
     * 로그인 - 이메일 또는 (예전에 만든) 아이디 + 비밀번호
     * 소셜 로그인 중 받은 연결 토큰이 있으면, 계정 주인임이 확인됐으므로 그 소셜 계정을 연결한다
     */
    @Transactional
    public LoginResponse login(UserLoginRequestDTO request) {
        String id = request.getLoginId().trim();
        Users user = (id.contains("@") ? userRepository.findByEmailIgnoreCase(id) : userRepository.findByLoginId(id))
                .orElseThrow(() -> new UsernameNotFoundException(LOGIN_FAILED));

        // 소셜 로그인으로만 가입한 계정은 비밀번호가 없다
        if (!user.hasPassword() || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException(LOGIN_FAILED);
        }

        if (StringUtils.hasText(request.getLinkToken())) {
            socialLoginService.completePendingLink(user, pendingLinkStore.consume(request.getLinkToken()));
        }

        TokenResponse tokens = refreshTokenService.issue(user.getUserId(), user.getLoginId());
        log.info("로그인 성공: userId={}", user.getUserId());
        return LoginResponse.of(user.getUserId(), user.getLoginId(), user.getNickname(),
                tokens.accessToken(), tokens.refreshToken());
    }

    /**
     * 회원가입 후 자동 로그인
     * - 이메일 가입(현재 방식): 이메일 인증 코드 확인 필수, 아이디 없음
     * - 아이디 가입(예전 앱 버전 호환): 아이디 필수, 이메일 선택
     */
    @Transactional
    public LoginResponse signup(UserCreateRequestDTO request) {
        String email = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null;
        boolean legacy = StringUtils.hasText(request.getLoginId());

        if (!legacy) {
            if (email == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
            if (!emailService.isVerified(email)) throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        } else if (emailVerificationRequired && (email == null || !emailService.isVerified(email))) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (legacy && userRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        String encoded = passwordEncoder.encode(request.getPassword());
        Users user = legacy
                ? Users.createLocal(email, request.getLoginId(), encoded, request.getNickname())
                : Users.createWithEmail(email, encoded, request.getNickname());
        if (legacy && email != null && emailService.isVerified(email)) {
            user.markEmailVerified();
        }

        Users savedUser = userRepository.save(user);
        accountSetupService.setUp(savedUser);

        if (email != null) {
            emailService.deleteVerification(email);
        }

        TokenResponse tokens = refreshTokenService.issue(savedUser.getUserId(), savedUser.getLoginId());
        log.info("회원가입 성공: userId={} ({})", savedUser.getUserId(), legacy ? "아이디" : "이메일");
        return LoginResponse.of(savedUser.getUserId(), savedUser.getLoginId(), savedUser.getNickname(),
                tokens.accessToken(), tokens.refreshToken());
    }

    /**
     * 토큰 재발급 - 저장된 유효한 리프레시 토큰이면 액세스·리프레시 토큰을 새로 발급 (쓴 리프레시 토큰은 폐기)
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        Long userId = refreshTokenService.consume(refreshToken);
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));  // 탈퇴한 사용자

        return refreshTokenService.issue(user.getUserId(), user.getLoginId());
    }

    /**
     * 로그아웃 - 이 기기의 리프레시 토큰을 무효화 (액세스 토큰은 클라이언트에서 삭제, 최대 30분 후 만료)
     */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }
}