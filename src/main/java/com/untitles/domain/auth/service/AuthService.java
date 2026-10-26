package com.untitles.domain.auth.service;

import com.untitles.domain.auth.dto.response.LoginResponse;
import com.untitles.domain.auth.dto.response.TokenResponse;
import com.untitles.domain.email.service.EmailService;
import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.dto.request.UserLoginRequestDTO;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
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
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.signup.email-verification-required:true}")
    private boolean emailVerificationRequired;

    /**
     * 로그인 - JWT 발급
     */
    public LoginResponse login(UserLoginRequestDTO request) {
        Users user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new UsernameNotFoundException("아이디가 존재하지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        TokenResponse tokens = refreshTokenService.issue(user.getUserId(), user.getLoginId());

        log.info("로그인 성공: {}", user.getLoginId());

        return LoginResponse.of(user.getUserId(), user.getLoginId(), user.getNickname(),
                tokens.accessToken(), tokens.refreshToken());
    }

    /**
     * 회원가입 후 자동 로그인 - JWT 발급
     */
    @Transactional
    public LoginResponse signup(UserCreateRequestDTO request) {
        String email = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null;

        if (emailVerificationRequired && (email == null || !emailService.isVerified(email))) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        Users user = Users.createLocal(
                email,
                request.getLoginId(),
                passwordEncoder.encode(request.getPassword()),
                request.getNickname()
        );

        Users savedUser = userRepository.save(user);

        Workspace personalWorkspace = Workspace.createPersonal();
        workspaceRepository.save(personalWorkspace);

        WorkspaceMember owner = WorkspaceMember.createOwner(personalWorkspace, savedUser);
        workspaceMemberRepository.save(owner);

        if (email != null) {
            emailService.deleteVerification(email);
        }

        TokenResponse tokens = refreshTokenService.issue(savedUser.getUserId(), savedUser.getLoginId());

        log.info("회원가입 성공: {}", savedUser.getLoginId());

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