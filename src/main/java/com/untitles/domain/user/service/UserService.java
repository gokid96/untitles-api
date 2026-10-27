package com.untitles.domain.user.service;

import com.untitles.domain.image.service.ImageService;
import com.untitles.domain.user.dto.request.UserUpdateRequestDTO;
import com.untitles.domain.user.dto.response.UserResponseDTO;
import com.untitles.domain.user.dto.response.UserSearchResponseDTO;
import com.untitles.domain.email.repository.EmailVerificationRepository;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.entity.WorkspaceType;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.service.WorkspaceService;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageService imageService;
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PostRepository postRepository;
    private final FolderRepository folderRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final com.untitles.domain.auth.service.RefreshTokenService refreshTokenService;
    private final com.untitles.domain.auth.repository.SocialAccountRepository socialAccountRepository;
    private final com.untitles.domain.auth.repository.PasswordResetRepository passwordResetRepository;
    private final com.untitles.domain.auth.social.AppleAuthClient appleAuthClient;

    private static final int SEARCH_MIN_LENGTH = 2;
    private static final int SEARCH_LIMIT = 10;

    /**
     * 조회
     */
    public UserResponseDTO getUserInfo(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toResponse(user);
    }

    private UserResponseDTO toResponse(Users user) {
        List<String> providers = socialAccountRepository.findAllByUserId(user.getUserId()).stream()
                .map(account -> account.getProvider().name())
                .distinct()
                .toList();
        return UserResponseDTO.from(user, providers);
    }

    /**
     * 수정
     */
    @Transactional
    public UserResponseDTO updateUser(Long userId, UserUpdateRequestDTO request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (StringUtils.hasText(request.getNickname())) {
            if (!user.getNickname().equals(request.getNickname())
                    && userRepository.existsByNickname(request.getNickname())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            user.updateNickname(request.getNickname());
        }
        if (request.getProfileImage() != null) {
            // 빈 값이면 프로필 이미지 제거, 아니면 본인이 업로드한 이미지만 허용
            String newProfileImage = StringUtils.hasText(request.getProfileImage()) ? request.getProfileImage() : null;
            if (newProfileImage != null && !imageService.isOwnProfileImage(newProfileImage, userId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            String oldProfileImage = user.getProfileImage();
            if (oldProfileImage != null && !oldProfileImage.equals(newProfileImage)) {
                deleteProfileImageQuietly(oldProfileImage, userId);
            }
            user.updateProfileImage(newProfileImage);
        }
        if (request.getEmail() != null) {
            String newEmail = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null;
            if (newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail())
                    && userRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            user.updateEmail(newEmail);
        }
        if (StringUtils.hasText(request.getNewPassword())) {
            // 비밀번호가 이미 있으면 현재 비밀번호 확인. 소셜 가입 계정은 처음 설정할 때 생략
            if (user.hasPassword()) {
                if (!StringUtils.hasText(request.getCurrentPassword())) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
                if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                    throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                }
            }
            user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
            // 다른 기기를 포함한 모든 로그인 무효화 (다시 로그인해야 함)
            refreshTokenService.revokeAll(user.getUserId());
        }

        userRepository.save(user);
        return toResponse(user);
    }

    /**
     * 탈퇴
     */
    @Transactional
    public void deleteUser(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<WorkspaceMember> memberships = workspaceMemberRepository.findAllByUserUserId(userId);
        List<Workspace> ownedWorkspaces = memberships.stream()
                .filter(m -> m.getRole() == WorkspaceRole.OWNER)
                .map(WorkspaceMember::getWorkspace)
                .toList();

        // 1. 다른 멤버가 남아 있는 팀 워크스페이스의 소유자는 탈퇴 불가 (남의 데이터가 함께 지워지므로)
        for (Workspace workspace : ownedWorkspaces) {
            if (workspace.getType() == WorkspaceType.TEAM && workspaceMemberRepository.countByWorkspace(workspace) > 1) {
                throw new BusinessException(ErrorCode.OWNER_HAS_TEAM_MEMBERS);
            }
        }

        // 2. 소유한 워크스페이스(개인 + 혼자 쓰는 팀) 전체 삭제
        ownedWorkspaces.forEach(workspaceService::deleteWorkspaceData);

        // 3. 다른 워크스페이스 멤버십 삭제, 남긴 글·폴더는 작성자만 비움
        workspaceMemberRepository.deleteAllByUserId(userId);
        postRepository.clearAuthor(userId);
        folderRepository.clearUser(userId);

        // 4. 이메일 인증 데이터, 프로필 이미지, 사용자 삭제
        if (StringUtils.hasText(user.getEmail())) {
            emailVerificationRepository.deleteByEmail(user.getEmail());
        }
        deleteProfileImageQuietly(user.getProfileImage(), userId);
        refreshTokenService.revokeAll(userId);
        passwordResetRepository.deleteByUserId(userId);

        // 5. 소셜 연결 삭제. 애플은 애플 쪽 연결도 끊는다 (App Store 심사 지침)
        socialAccountRepository.findAllByUserId(userId).stream()
                .filter(account -> account.getAppleRefreshToken() != null)
                .forEach(account -> appleAuthClient.revoke(account.getAppleRefreshToken(), account.getAppleClientId()));
        socialAccountRepository.deleteAllByUserId(userId);

        userRepository.delete(user);
    }

    /**
     * 본인 경로의 프로필 이미지만 삭제 (실패해도 진행)
     */
    private void deleteProfileImageQuietly(String imageUrl, Long userId) {
        try {
            imageService.deleteProfileImage(imageUrl, userId);
        } catch (Exception e) {
            log.warn("프로필 이미지 삭제 실패: {}", imageUrl, e);
        }
    }

    /**
     * 이메일 또는 닉네임으로 사용자 검색 (멤버 초대용)
     */
    public List<UserSearchResponseDTO> searchUsers(String query, Long currentUserId) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < SEARCH_MIN_LENGTH) {
            return List.of();
        }
        String likeQuery = trimmed.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return userRepository.searchForInvite(trimmed, likeQuery, currentUserId, PageRequest.of(0, SEARCH_LIMIT))
                .stream()
                .map(UserSearchResponseDTO::from)
                .collect(Collectors.toList());
    }
}
