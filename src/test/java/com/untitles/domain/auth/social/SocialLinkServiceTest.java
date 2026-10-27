package com.untitles.domain.auth.social;

import com.untitles.domain.auth.repository.SocialAccountRepository;
import com.untitles.domain.auth.entity.SocialAccount;
import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.ErrorCode;
import com.untitles.support.H2JpaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@H2JpaTest
@Import(SocialLinkService.class)
class SocialLinkServiceTest {

    @Autowired SocialLinkService socialLinkService;
    @Autowired UserRepository userRepository;
    @Autowired SocialAccountRepository socialAccountRepository;
    @MockitoBean IdTokenVerifier idTokenVerifier;
    @MockitoBean AppleAuthClient appleAuthClient;

    private SocialProfile kakao(String subject, String email) {
        return new SocialProfile(AuthProvider.KAKAO, subject, email, true, "카카오", null);
    }

    @Test
    @DisplayName("내 정보에서 연결하면 그 소셜 계정으로 내 계정에 로그인할 수 있고, 같은 이메일이면 이메일도 확인된다")
    void link() {
        Users me = userRepository.save(Users.createLocal("me@example.com", "me", "hashed", "나"));

        socialLinkService.link(me.getUserId(), kakao("k-1", "me@example.com"));

        assertThat(socialAccountRepository.findByProviderAndSubject(AuthProvider.KAKAO, "k-1"))
                .get().extracting(SocialAccount::getUserId).isEqualTo(me.getUserId());
        assertThat(me.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("다른 계정에 연결된 소셜 계정, 이미 같은 종류가 연결된 경우는 막는다")
    void conflicts() {
        Users a = userRepository.save(Users.createWithEmail("a@example.com", "hashed", "에이"));
        Users b = userRepository.save(Users.createWithEmail("b@example.com", "hashed", "비"));
        socialLinkService.link(a.getUserId(), kakao("k-2", null));

        assertThatThrownBy(() -> socialLinkService.link(b.getUserId(), kakao("k-2", null)))
                .extracting("errorCode").isEqualTo(ErrorCode.SOCIAL_ACCOUNT_IN_USE);
        assertThatThrownBy(() -> socialLinkService.link(a.getUserId(), kakao("k-3", null)))
                .extracting("errorCode").isEqualTo(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
    }

    @Test
    @DisplayName("비밀번호가 없는 계정은 마지막 로그인 방법을 해제할 수 없다")
    void unlinkLast() {
        Users social = userRepository.save(Users.createSocial("s@example.com", "소셜", null));
        socialLinkService.link(social.getUserId(), kakao("k-4", null));

        assertThatThrownBy(() -> socialLinkService.unlink(social.getUserId(), AuthProvider.KAKAO))
                .extracting("errorCode").isEqualTo(ErrorCode.LAST_LOGIN_METHOD);

        socialLinkService.link(social.getUserId(), new SocialProfile(AuthProvider.GOOGLE, "g-1", null, false, null, null));
        socialLinkService.unlink(social.getUserId(), AuthProvider.KAKAO);
        assertThat(socialAccountRepository.findAllByUserId(social.getUserId()))
                .extracting(SocialAccount::getProvider).containsExactly(AuthProvider.GOOGLE);
    }
}
