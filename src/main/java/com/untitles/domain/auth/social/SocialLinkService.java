package com.untitles.domain.auth.social;

import com.untitles.domain.auth.entity.SocialAccount;
import com.untitles.domain.auth.repository.SocialAccountRepository;
import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 로그인한 상태에서 소셜 계정 연결·해제 (내 정보 > 로그인 방법)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLinkService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final IdTokenVerifier idTokenVerifier;
    private final AppleAuthClient appleAuthClient;

    /** 제공자가 확인해 준 소셜 계정을 이 사용자에게 연결한다 */
    @Transactional
    public SocialAccount link(Long userId, SocialProfile profile) {
        Users user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Optional<SocialAccount> existing = socialAccountRepository.findByProviderAndSubject(profile.provider(), profile.subject());
        if (existing.isPresent()) {
            if (existing.get().getUserId().equals(userId)) return existing.get();  // 이미 연결됨
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_IN_USE);
        }
        if (socialAccountRepository.existsByUserIdAndProvider(userId, profile.provider())) {
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
        }

        String email = profile.emailVerified() ? profile.email() : null;
        SocialAccount account = socialAccountRepository.save(
                SocialAccount.of(userId, profile.provider(), profile.subject(), email));
        // 프로필 사진이 없으면 연결한 계정의 사진을 기본으로 쓴다 (이미 있으면 그대로 둔다)
        if (user.getProfileImage() == null && profile.safeProfileImage() != null) {
            user.updateProfileImage(profile.safeProfileImage());
        }
        // 제공자가 확인한 이메일이 내 이메일과 같으면 내 이메일도 확인된 것으로
        if (email != null && user.getEmail() != null && user.getEmail().equalsIgnoreCase(email)) {
            user.markEmailVerified();
        }
        log.info("소셜 계정 연결(내 정보): userId={}, provider={}", userId, profile.provider());
        return account;
    }

    @Transactional
    public void linkGoogle(Long userId, String idToken) {
        link(userId, idTokenVerifier.verifyGoogle(idToken));
    }

    @Transactional
    public void linkApple(Long userId, String idToken, String authorizationCode, String rawNonce) {
        IdTokenVerifier.VerifiedApple verified = idTokenVerifier.verifyApple(idToken, rawNonce, null);
        SocialAccount account = link(userId, verified.profile());
        if (account.getAppleRefreshToken() == null) {
            String refreshToken = appleAuthClient.exchangeCode(authorizationCode, verified.clientId());
            if (refreshToken != null) account.updateAppleToken(refreshToken, verified.clientId());
        }
    }

    /** 연결 해제. 로그인할 방법이 하나도 남지 않으면 막는다 */
    @Transactional
    public void unlink(Long userId, AuthProvider provider) {
        Users user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<SocialAccount> accounts = socialAccountRepository.findAllByUserId(userId);
        List<SocialAccount> targets = accounts.stream().filter(a -> a.getProvider() == provider).toList();
        if (targets.isEmpty()) return;
        if (!user.hasPassword() && accounts.size() == targets.size()) {
            throw new BusinessException(ErrorCode.LAST_LOGIN_METHOD);
        }
        targets.stream()
                .filter(a -> a.getAppleRefreshToken() != null)
                .forEach(a -> appleAuthClient.revoke(a.getAppleRefreshToken(), a.getAppleClientId()));
        socialAccountRepository.deleteAll(targets);
        log.info("소셜 계정 연결 해제: userId={}, provider={}", userId, provider);
    }
}
