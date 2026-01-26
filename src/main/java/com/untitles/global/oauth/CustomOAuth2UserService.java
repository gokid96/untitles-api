package com.untitles.global.oauth;

import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.entity.WorkspaceType;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(provider, oAuth2User.getAttributes());

        String email = userInfo.getEmail();
        String providerId = userInfo.getProviderId();

        // 기존 회원 확인 (이메일 또는 provider+providerId로)
        Optional<Users> existingUser = userRepository.findByEmail(email);

        Users user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            // 기존 LOCAL 회원이 소셜 로그인 시 provider 정보 업데이트
            if (user.getProvider() == null || user.getProvider() == AuthProvider.LOCAL) {
                user.updateOAuthInfo(provider, providerId);
            }
        } else {
            // 신규 회원 자동 가입
            user = registerNewUser(userInfo, provider, providerId);
        }

        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    private Users registerNewUser(OAuth2UserInfo userInfo, AuthProvider provider, String providerId) {
        String nickname = generateUniqueNickname(userInfo.getNickname());

        Users newUser = Users.builder()
                .email(userInfo.getEmail())
                .nickname(nickname)
                .profileImage(userInfo.getProfileImage())
                .provider(provider)
                .providerId(providerId)
                .build();

        Users savedUser = userRepository.save(newUser);

        // 개인 워크스페이스 자동 생성
        createPersonalWorkspace(savedUser);

        log.info("새 OAuth 사용자 등록: {} ({})", savedUser.getEmail(), provider);

        return savedUser;
    }

    private String generateUniqueNickname(String baseNickname) {
        String nickname = baseNickname;
        if (nickname == null || nickname.isBlank()) {
            nickname = "user";
        }
        
        // 닉네임 길이 제한 (50자)
        if (nickname.length() > 40) {
            nickname = nickname.substring(0, 40);
        }

        // 중복 체크 후 숫자 추가
        String finalNickname = nickname;
        int count = 1;
        while (userRepository.existsByNickname(finalNickname)) {
            finalNickname = nickname + "_" + count++;
        }

        return finalNickname;
    }

    private void createPersonalWorkspace(Users user) {
        Workspace workspace = Workspace.builder()
                .name("내 워크스페이스")
                .type(WorkspaceType.PERSONAL)
                .build();

        workspaceRepository.save(workspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build();

        workspaceMemberRepository.save(member);
    }
}