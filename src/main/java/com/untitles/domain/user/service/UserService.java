package com.untitles.domain.user.service;

import com.untitles.domain.image.service.ImageService;
import com.untitles.domain.user.dto.request.UserUpdateRequestDTO;
import com.untitles.domain.user.dto.response.UserResponseDTO;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
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
            String oldProfileImage = user.getProfileImage();
            try {
                if (StringUtils.hasText(oldProfileImage)) {
                    imageService.delete(oldProfileImage);
                }
            } catch (Exception e) {
                log.warn("프로필 이미지 삭제 실패:{}", oldProfileImage, e);
            }
            user.updateProfileImage(request.getProfileImage());
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
        try {
            if (StringUtils.hasText(user.getProfileImage())) {
                imageService.delete(user.getProfileImage());
            }
        } catch (Exception e) {
            log.warn("프로필 이미지 삭제 실패: {}", user.getProfileImage(), e);
        }
        userRepository.delete(user);
    }

    /**
     * 이메일 또는 닉네임으로 사용자 검색 (멤버 초대용)
     */
    public List<UserResponseDTO> searchUsers(String query, Long currentUserId) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        return userRepository.findByEmailOrNicknameContaining(query).stream()
                .filter(user -> !user.getUserId().equals(currentUserId))
                .limit(10)
                .map(UserResponseDTO::from)
                .collect(Collectors.toList());
    }
}
