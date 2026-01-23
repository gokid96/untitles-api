package com.untitles.domain.user.service;

import com.untitles.domain.image.service.ImageService;
import com.untitles.domain.user.dto.request.UserCreateRequestDTO;
import com.untitles.domain.user.dto.request.UserLoginRequestDTO;
import com.untitles.domain.user.dto.request.UserUpdateRequestDTO;
import com.untitles.domain.user.dto.response.UserResponseDTO;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageService imageService;

    /**
     * 생성
     */
    public UserResponseDTO createUser(UserCreateRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용중인 이메일입니다.");
        }
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new IllegalArgumentException("이미 사용중인 아이디입니다.");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        Users user = Users.builder()
                .email(request.getEmail())
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        Users savedUser = userRepository.save(user);
        return UserResponseDTO.from(savedUser);
    }

    /**
     * 로그인
     */
    public UserResponseDTO login(UserLoginRequestDTO request) {
        Users user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디가 존재하지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return UserResponseDTO.from(user);
    }

    /**
     * 조회
     */
    public UserResponseDTO getUserInfo(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return UserResponseDTO.from(user);
    }

    /**
     * 수정
     */
    public UserResponseDTO updateUser(Long userId, UserUpdateRequestDTO request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 닉네임 수정 (값이 있을 때만)
        if (StringUtils.hasText(request.getNickname())) {
            // 다른 사용자가 사용 중인 닉네임인지 확인
            if (!user.getNickname().equals(request.getNickname())
                    && userRepository.existsByNickname(request.getNickname())) {
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
            }
            user.updateNickname(request.getNickname());
        }
        // 프로필 이미지 수정 (닉네임 수정 아래에 추가)
        if (request.getProfileImage() != null) {
            //기존 이미지 R2에서 삭제
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
        // 비밀번호 수정 (새 비밀번호가 있을 때만)
        if (StringUtils.hasText(request.getNewPassword())) {
            // 현재 비밀번호 확인 필수
            if (!StringUtils.hasText(request.getCurrentPassword())) {
                throw new IllegalArgumentException("현재 비밀번호를 입력해주세요.");
            }
            // 현재 비밀번호 검증
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
            }
            user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        }

        userRepository.save(user);
        return UserResponseDTO.from(user);
    }

    /**
     * 삭제
     */
    public void deleteUser(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        try {
            // 프로필 이미지 R2에서 삭제
            if (StringUtils.hasText(user.getProfileImage())) {
                imageService.delete(user.getProfileImage());
            }
        } catch (Exception e) {
            log.warn("프로필 이미지 삭제 실패: {}", user.getProfileImage(), e);
        }
        userRepository.delete(user);
    }

    /**
     * 이메일 또는 닉네임으로 사용자 검색
     */
    public List<UserResponseDTO> searchUsers(String query, Long currentUserId) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        return userRepository.findByEmailOrNicknameContaining(query).stream()
                .filter(user -> !user.getUserId().equals(currentUserId)) // 본인 제외
                .limit(10) // 최대 10개
                .map(UserResponseDTO::from)
                .collect(Collectors.toList());
    }
}
