package com.untitles.domain.user.service;

import com.untitles.domain.image.service.ImageService;
import com.untitles.domain.user.dto.request.UserUpdateRequestDTO;
import com.untitles.domain.user.dto.response.UserResponseDTO;
import com.untitles.domain.user.dto.response.UserSearchResponseDTO;
import com.untitles.domain.user.entity.Users;
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

    private static final int SEARCH_MIN_LENGTH = 2;
    private static final int SEARCH_LIMIT = 10;

    /**
     * 조회
     */
    public UserResponseDTO getUserInfo(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponseDTO.from(user);
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
        if (StringUtils.hasText(request.getNewPassword())) {
            if (!StringUtils.hasText(request.getCurrentPassword())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        }

        userRepository.save(user);
        return UserResponseDTO.from(user);
    }

    /**
     * 탈퇴
     */
    @Transactional
    public void deleteUser(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        deleteProfileImageQuietly(user.getProfileImage(), userId);
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
