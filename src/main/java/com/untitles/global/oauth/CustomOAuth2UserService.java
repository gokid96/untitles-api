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
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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

        // 이메일 필수 검증
        String email = userInfo.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("OAuth2 로그인 실패: 이메일 미제공 (provider: {})", provider);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_required", "이메일 제공에 동의해주세요.", null)
            );
        }

        String providerId = userInfo.getProviderId();
        if (providerId == null || providerId.isBlank()) {
            log.warn("OAuth2 로그인 실패: providerId 없음 (provider: {})", provider);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_provider_id", "소셜 계정 정보를 가져올 수 없습니다.", null)
            );
        }

        // 기존 회원 확인
        Optional<Users> existingUser = userRepository.findByEmail(email);

        Users user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            user = handleExistingUser(user, provider, providerId);
        } else {
            user = registerNewUser(userInfo, provider, providerId);
        }

        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    private Users handleExistingUser(Users user, AuthProvider provider, String providerId) {
        AuthProvider existingProvider = user.getProvider();

        // LOCAL 계정 → OAuth 연동
        if (existingProvider == null || existingProvider == AuthProvider.LOCAL) {
            user.updateOAuthInfo(provider, providerId);
            log.info("기존 LOCAL 계정에 OAuth 연동: {} ({})", user.getEmail(), provider);
            return user;
        }

        // 동일 provider로 로그인
        if (existingProvider == provider) {
            if (!providerId.equals(user.getProviderId())) {
                log.warn("OAuth providerId 불일치: email={}, 기존={}, 신규={}",
                        user.getEmail(), user.getProviderId(), providerId);
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("provider_id_mismatch", "계정 정보가 일치하지 않습니다.", null)
                );
            }
            return user;
        }

        // 다른 provider로 로그인 시도 → 차단
        log.warn("다른 OAuth provider로 로그인 시도: email={}, 기존={}, 시도={}",
                user.getEmail(), existingProvider, provider);
        throw new OAuth2AuthenticationException(
                new OAuth2Error("account_exists",
                        existingProvider.name() + " 계정으로 이미 가입되어 있습니다.", null)
        );
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
        createPersonalWorkspace(savedUser);

        log.info("새 OAuth 사용자 등록: {} ({})", savedUser.getEmail(), provider);

        return savedUser;
    }

    private String generateUniqueNickname(String baseNickname) {
        String nickname = baseNickname;
        if (nickname == null || nickname.isBlank()) {
            nickname = "user";
        }

        if (nickname.length() > 40) {
            nickname = nickname.substring(0, 40);
        }

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