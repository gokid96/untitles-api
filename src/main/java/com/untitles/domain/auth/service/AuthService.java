package com.untitles.domain.auth.service;

import com.untitles.domain.auth.dto.response.LoginResponse;
import com.untitles.domain.email.service.EmailService;
import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.dto.request.UserLoginRequestDTO;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.entity.WorkspaceType;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final WorkspaceRepository  workspaceRepository;
    private final WorkspaceMemberRepository  workspaceMemberRepository;
    /**
     * 로그인 - 세션 기반
     */
    public LoginResponse login(UserLoginRequestDTO request, HttpSession session) {
        // 사용자 조회
        Users user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new UsernameNotFoundException("아이디가 존재하지 않습니다."));

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        // SecurityContext에 인증 정보 저장
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // 세션에 SecurityContext 저장 (Spring Security가 자동으로 처리하지만 명시적으로)
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );

        log.info("로그인 성공: {}", user.getLoginId());

        return LoginResponse.of(
                user.getUserId(),
                user.getLoginId(),
                user.getNickname()
        );
    }

    /**
     * 회원가입
     */
    @Transactional
    public LoginResponse signup(UserCreateRequestDTO request, HttpSession session) {
        if (!emailService.isVerified(request.getEmail())) {
            throw new IllegalStateException("이메일 인증이 필요합니다.");
        }
        // 중복 검사
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용중인 이메일입니다.");
        }
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new IllegalArgumentException("이미 사용중인 아이디입니다.");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용중인 닉네임입니다.");
        }

        // 사용자 생성
        Users user = Users.builder()
                .email(request.getEmail())
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        Users savedUser = userRepository.save(user);

        // 회원가입 후 개인 워크스페이스 자동 생성
        Workspace personalWorkspace = Workspace.builder()
                .name("개인 워크스페이스")
                .type(WorkspaceType.PERSONAL)
                .build();
        workspaceRepository.save(personalWorkspace);

        WorkspaceMember owner = WorkspaceMember.builder()
                .workspace(personalWorkspace)
                .user(savedUser)
                .role(WorkspaceRole.OWNER)
                .build();
        workspaceMemberRepository.save(owner);

        emailService.deleteVerification(request.getEmail());

        // 회원가입 후 자동 로그인 (세션 생성)
        CustomUserDetails userDetails = new CustomUserDetails(savedUser);
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );

        log.info("회원가입 성공: {}", savedUser.getLoginId());

        return LoginResponse.of(
                savedUser.getUserId(),
                savedUser.getLoginId(),
                savedUser.getNickname()
        );
    }

    /**
     * 로그아웃
     */
    public void logout(HttpSession session) {
        SecurityContextHolder.clearContext();
        session.invalidate();
        log.info("로그아웃 성공");
    }
}
