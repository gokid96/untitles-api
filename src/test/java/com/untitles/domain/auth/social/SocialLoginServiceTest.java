package com.untitles.domain.auth.social;

import com.untitles.domain.auth.repository.SocialAccountRepository;
import com.untitles.domain.auth.service.AccountSetupService;
import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.global.exception.ErrorCode;
import com.untitles.support.H2JpaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@H2JpaTest
@Import({SocialLoginService.class, AccountSetupService.class, PendingLinkStore.class})
class SocialLoginServiceTest {

    @Autowired SocialLoginService socialLoginService;
    @Autowired UserRepository userRepository;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired PendingLinkStore pendingLinkStore;

    private SocialProfile kakao(String subject, String email, boolean verified) {
        return new SocialProfile(AuthProvider.KAKAO, subject, email, verified, "카카오친구", null);
    }

    @Test
    @DisplayName("처음이면 새 계정과 개인 워크스페이스를 만들고, 다음부터는 같은 계정으로 로그인한다")
    void newThenSame() {
        var first = socialLoginService.login(kakao("k-1", "a@example.com", true));
        var second = socialLoginService.login(kakao("k-1", "a@example.com", true));

        assertThat(first.newUser()).isTrue();
        assertThat(second.user().getUserId()).isEqualTo(first.user().getUserId());
        assertThat(first.user().getPassword()).isNull();
        assertThat(first.user().isEmailVerified()).isTrue();
        assertThat(memberRepository.findAllByUserUserId(first.user().getUserId())).hasSize(1);
    }

    @Test
    @DisplayName("이메일이 확인된 기존 계정과는 자동으로 연결하고, 다른 제공자도 같은 계정에 붙는다")
    void linksVerifiedEmail() {
        Users existing = Users.createWithEmail("me@example.com", "hashed", "기존사용자");
        userRepository.save(existing);

        var kakaoResult = socialLoginService.login(kakao("k-2", "me@example.com", true));
        var apple = socialLoginService.login(new SocialProfile(AuthProvider.APPLE, "a-2", "me@example.com", true, null, null));

        assertThat(kakaoResult.user().getUserId()).isEqualTo(existing.getUserId());
        assertThat(apple.user().getUserId()).isEqualTo(existing.getUserId());
        assertThat(socialAccountRepository.findAllByUserId(existing.getUserId())).hasSize(2);
    }

    @Test
    @DisplayName("이메일이 확인되지 않은 기존 계정과는 연결하지 않는다 (남이 등록한 이메일일 수 있음)")
    void refusesUnverifiedEmail() {
        userRepository.save(Users.createLocal("victim@example.com", "attacker", "hashed", "공격자"));

        assertThatThrownBy(() -> socialLoginService.login(kakao("k-3", "victim@example.com", true)))
                .extracting("errorCode").isEqualTo(ErrorCode.SOCIAL_EMAIL_IN_USE);
        assertThat(socialAccountRepository.findByProviderAndSubject(AuthProvider.KAKAO, "k-3")).isEmpty();
    }

    @Test
    @DisplayName("기존 계정 비밀번호로 로그인하면 남겨 둔 연결 요청으로 소셜 계정을 연결하고 이메일을 확인 처리한다")
    void linksAfterPasswordLogin() {
        Users existing = userRepository.save(Users.createLocal("old@example.com", "olduser", "hashed", "예전사용자"));

        SocialLinkRequiredException e = (SocialLinkRequiredException) org.assertj.core.api.Assertions.catchThrowable(
                () -> socialLoginService.login(kakao("k-5", "old@example.com", true)));
        assertThat(e.getEmail()).isEqualTo("old@example.com");

        socialLoginService.completePendingLink(existing, pendingLinkStore.consume(e.getLinkToken()));

        assertThat(existing.isEmailVerified()).isTrue();
        var again = socialLoginService.login(kakao("k-5", "old@example.com", true));
        assertThat(again.user().getUserId()).isEqualTo(existing.getUserId());
        assertThat(again.newUser()).isFalse();
        // 토큰은 한 번만 쓸 수 있다
        assertThat(pendingLinkStore.consume(e.getLinkToken())).isNull();
    }

    @Test
    @DisplayName("연결 요청은 그 요청을 만든 계정으로 로그인했을 때만 처리한다")
    void ignoresLinkForOtherAccount() {
        userRepository.save(Users.createLocal("target@example.com", "target", "hashed", "대상"));
        Users other = userRepository.save(Users.createWithEmail("other@example.com", "hashed", "다른사람"));

        SocialLinkRequiredException e = (SocialLinkRequiredException) org.assertj.core.api.Assertions.catchThrowable(
                () -> socialLoginService.login(kakao("k-6", "target@example.com", true)));
        socialLoginService.completePendingLink(other, pendingLinkStore.consume(e.getLinkToken()));

        assertThat(socialAccountRepository.findByProviderAndSubject(AuthProvider.KAKAO, "k-6")).isEmpty();
    }

    @Test
    @DisplayName("제공자가 확인하지 않은 이메일은 쓰지 않고 이메일 없이 가입한다")
    void ignoresUnverifiedProviderEmail() {
        userRepository.save(Users.createWithEmail("someone@example.com", "hashed", "누군가"));

        var result = socialLoginService.login(kakao("k-4", "someone@example.com", false));

        assertThat(result.newUser()).isTrue();
        assertThat(result.user().getEmail()).isNull();
        assertThat(result.user().getNickname()).isEqualTo("카카오친구");
    }
}
