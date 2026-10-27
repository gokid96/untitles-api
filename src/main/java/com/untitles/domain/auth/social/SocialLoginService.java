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
 *    (확인 안 된 이메일 계정은 남이 등록했을 수도 있으므로 자동 연결하지 않는다)
 * 3. 그 밖에는 새 계정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final AccountSetupService accountSetupService;

    public record Result(Users user, SocialAccount account, boolean newUser) {
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
                    // 확인 안 된 이메일 계정과는 자동으로 합치지 않는다
                    throw new BusinessException(ErrorCode.SOCIAL_EMAIL_IN_USE);
                }
                SocialAccount account = socialAccountRepository.save(
                        SocialAccount.of(existing.getUserId(), profile.provider(), profile.subject(), email));
                log.info("소셜 계정 연결: userId={}, provider={}", existing.getUserId(), profile.provider());
                return new Result(existing, account, false);
            }
        }

        Users user = userRepository.save(Users.createSocial(email,
                accountSetupService.uniqueNickname(profile.nickname()), profile.profileImage()));
        accountSetupService.setUp(user);
        SocialAccount account = socialAccountRepository.save(
                SocialAccount.of(user.getUserId(), profile.provider(), profile.subject(), email));
        log.info("소셜 가입: userId={}, provider={}", user.getUserId(), profile.provider());
        return new Result(user, account, true);
    }
}
