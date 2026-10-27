package com.untitles.domain.auth.social;

import com.untitles.domain.auth.entity.SocialAccount;
import com.untitles.domain.auth.repository.SocialAccountRepository;
import com.untitles.domain.auth.service.AccountSetupService;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 소셜 로그인 공통 처리 (웹 리다이렉트 방식, 앱의 ID 토큰 방식 모두)
 *
 * 1. (제공자, 제공자 사용자 ID)로 연결된 계정이 있으면 그 계정
 * 2. 없으면, 제공자가 확인한 이메일과 같은 이메일을 가진 계정이 있고 그 계정도 이메일을 확인했으면 연결
 *    (확인 안 된 이메일 계정은 남이 등록했을 수도 있으므로 자동 연결하지 않고,
 *     그 계정 비밀번호로 로그인하면 연결되도록 SocialLinkRequiredException으로 알린다)
 * 3. 그 밖에는 새 계정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final AccountSetupService accountSetupService;
    private final PendingLinkStore pendingLinkStore;

    public record Result(Users user, SocialAccount account, boolean newUser) {
    }

    /** 비밀번호 로그인으로 계정 주인임이 확인된 뒤, 남겨 둔 연결 요청을 처리한다 */
    @Transactional
    public void completePendingLink(Users user, PendingLinkStore.PendingLink link) {
        if (link == null || !link.userId().equals(user.getUserId())) return;
        if (socialAccountRepository.findByProviderAndSubject(link.provider(), link.subject()).isEmpty()) {
            SocialAccount account = SocialAccount.of(user.getUserId(), link.provider(), link.subject(), link.email());
            if (link.appleRefreshToken() != null) {
                account.updateAppleToken(link.appleRefreshToken(), link.appleClientId());
            }
            socialAccountRepository.save(account);
            log.info("로그인 후 소셜 계정 연결: userId={}, provider={}", user.getUserId(), link.provider());
        }
        // 제공자가 확인한 이메일과 같고, 비밀번호로 계정 주인임도 확인됐으므로
        if (user.getEmail() != null && user.getEmail().equalsIgnoreCase(link.email())) {
            user.markEmailVerified();
        }
    }

    @Transactional
    public Result login(SocialProfile profile) {
        if (!StringUtils.hasText(profile.subject())) {
            throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }

        Optional<SocialAccount> linked = socialAccountRepository.findByProviderAndSubject(profile.provider(), profile.subject());
        if (linked.isPresent()) {
            Users user = userRepository.findById(linked.get().getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            return new Result(user, linked.get(), false);
        }

        String email = profile.emailVerified() && StringUtils.hasText(profile.email()) ? profile.email().trim() : null;

        if (email != null) {
            Optional<Users> sameEmail = userRepository.findByEmailIgnoreCase(email);
            if (sameEmail.isPresent()) {
                Users existing = sameEmail.get();
                if (!existing.isEmailVerified()) {
                    // 확인 안 된 이메일 계정과는 자동으로 합치지 않는다.
                    // 대신 그 계정 비밀번호로 로그인하면 연결되도록 연결 요청을 남긴다
                    String token = pendingLinkStore.issue(new PendingLinkStore.PendingLink(
                            existing.getUserId(), profile.provider(), profile.subject(), email, null, null));
                    throw new SocialLinkRequiredException(token, existing.getEmail(), profile.provider());
                }
                SocialAccount account = socialAccountRepository.save(
                        SocialAccount.of(existing.getUserId(), profile.provider(), profile.subject(), email));
                log.info("소셜 계정 연결: userId={}, provider={}", existing.getUserId(), profile.provider());
                return new Result(existing, account, false);
            }
        }

        Users user = userRepository.save(Users.createSocial(email,
                accountSetupService.uniqueNickname(profile.nickname()), profile.safeProfileImage()));
        accountSetupService.setUp(user);
        SocialAccount account = socialAccountRepository.save(
                SocialAccount.of(user.getUserId(), profile.provider(), profile.subject(), email));
        log.info("소셜 가입: userId={}, provider={}", user.getUserId(), profile.provider());
        return new Result(user, account, true);
    }
}
