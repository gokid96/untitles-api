package com.untitles.domain.auth.service;

import com.untitles.domain.auth.dto.response.LoginResponse;
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
import com.untitles.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
    private final JwtProvider jwtProvider;

    /**
     * 로그인 - JWT 발급
     */
    public LoginResponse login(UserLoginRequestDTO request) {
        Users user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new UsernameNotFoundException("아이디가 존재하지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getLoginId());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId(), user.getLoginId());

        log.info("로그인 성공: {}", user.getLoginId());

        return LoginResponse.of(user.getUserId(), user.getLoginId(), user.getNickname(),
                accessToken, refreshToken);
    }

    /**
     * 회원가입 후 자동 로그인 - JWT 발급
     */
    @Transactional
    public LoginResponse signup(UserCreateRequestDTO request) {
        if (!emailService.isVerified(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        Users user = Users.createLocal(
                request.getEmail(),
                request.getLoginId(),
                passwordEncoder.encode(request.getPassword()),
                request.getNickname()
        );

        Users savedUser = userRepository.save(user);

        Workspace personalWorkspace = Workspace.createPersonal();
        workspaceRepository.save(personalWorkspace);

        WorkspaceMember owner = WorkspaceMember.createOwner(personalWorkspace, savedUser);
        workspaceMemberRepository.save(owner);

        emailService.deleteVerification(request.getEmail());

        String accessToken = jwtProvider.createAccessToken(savedUser.getUserId(), savedUser.getLoginId());
        String refreshToken = jwtProvider.createRefreshToken(savedUser.getUserId(), savedUser.getLoginId());

        log.info("회원가입 성공: {}", savedUser.getLoginId());

        return LoginResponse.of(savedUser.getUserId(), savedUser.getLoginId(), savedUser.getNickname(),
                accessToken, refreshToken);
    }

    /**
     * 로그아웃 - JWT는 클라이언트에서 토큰 삭제로 처리
     * 서버에서는 별도 처리 없음
     */
    public void logout() {
        log.info("로그아웃");
    }
}