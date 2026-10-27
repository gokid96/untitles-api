package com.untitles.domain.user.service;

import com.untitles.domain.image.service.ImageService;
import com.untitles.domain.user.dto.request.UserUpdateRequestDTO;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ImageService imageService;
    @Mock private com.untitles.domain.auth.service.RefreshTokenService refreshTokenService;
    @Mock private com.untitles.domain.auth.repository.SocialAccountRepository socialAccountRepository;
    @Mock private com.untitles.domain.auth.repository.PasswordResetRepository passwordResetRepository;
    @Mock private com.untitles.domain.auth.social.AppleAuthClient appleAuthClient;

    @InjectMocks
    private UserService userService;

    private Users user(String profileImage) {
        return Users.builder().userId(7L).loginId("tester").nickname("테스터").profileImage(profileImage).build();
    }

    @Test
    @DisplayName("본인이 올리지 않은 이미지 URL로는 프로필을 바꿀 수 없다")
    void rejectsForeignProfileImage() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user("https://img/profiles/7/old.png")));
        when(imageService.isOwnProfileImage("https://img/profiles/8/victim.png", 7L)).thenReturn(false);

        UserUpdateRequestDTO request = UserUpdateRequestDTO.builder()
                .profileImage("https://img/profiles/8/victim.png").build();

        assertThatThrownBy(() -> userService.updateUser(7L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verify(imageService, never()).deleteProfileImage(any(), anyLong());
    }

    @Test
    @DisplayName("본인 이미지로 바꾸면 이전 이미지 삭제를 요청한다")
    void replacesOwnProfileImage() {
        Users user = user("https://img/profiles/7/old.png");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(imageService.isOwnProfileImage("https://img/profiles/7/new.png", 7L)).thenReturn(true);

        userService.updateUser(7L, UserUpdateRequestDTO.builder()
                .profileImage("https://img/profiles/7/new.png").build());

        verify(imageService).deleteProfileImage("https://img/profiles/7/old.png", 7L);
        assertThat(user.getProfileImage()).isEqualTo("https://img/profiles/7/new.png");
    }

    @Test
    @DisplayName("빈 값을 보내면 프로필 이미지를 제거한다")
    void clearsProfileImage() {
        Users user = user("https://img/profiles/7/old.png");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        userService.updateUser(7L, UserUpdateRequestDTO.builder().profileImage("").build());

        assertThat(user.getProfileImage()).isNull();
        verify(imageService).deleteProfileImage("https://img/profiles/7/old.png", 7L);
    }

    @Test
    @DisplayName("검색어가 2글자 미만이면 DB를 조회하지 않는다")
    void searchMinLength() {
        assertThat(userService.searchUsers("a", 7L)).isEmpty();
        assertThat(userService.searchUsers("  ", 7L)).isEmpty();
        verifyNoInteractions(userRepository);
    }
}
