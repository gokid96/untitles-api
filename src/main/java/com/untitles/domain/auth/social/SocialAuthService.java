package com.untitles.domain.auth.social;

import com.untitles.domain.auth.dto.response.LoginResponse;
import com.untitles.domain.auth.dto.response.TokenResponse;
import com.untitles.domain.auth.service.RefreshTokenService;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱의 ID 토큰 로그인(구글·애플)과, 웹 리다이렉트 로그인의 일회용 코드 교환
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final IdTokenVerifier idTokenVerifier;
    private final SocialLoginService socialLoginService;
    private final AppleAuthClient appleAuthClient;
    private final RefreshTokenService refreshTokenService;
    private final LoginCodeStore loginCodeStore;
    private final UserRepository userRepository;

    @Transactional
    public LoginResponse loginWithGoogle(String idToken) {
        SocialLoginService.Result result = socialLoginService.login(idTokenVerifier.verifyGoogle(idToken));
        return issue(result.user());
    }

    @Transactional
    public LoginResponse loginWithApple(String idToken, String authorizationCode, String rawNonce, String name) {
        IdTokenVerifier.VerifiedApple verified = idTokenVerifier.verifyApple(idToken, rawNonce, name);
        SocialLoginService.Result result = socialLoginService.login(verified.profile());

        // 탈퇴 때 애플 연결을 끊을 수 있도록 리프레시 토큰을 받아 둔다 (처음 한 번, 또는 아직 없으면)
        if (result.account().getAppleRefreshToken() == null) {
            String refreshToken = appleAuthClient.exchangeCode(authorizationCode, verified.clientId());
            if (refreshToken != null) {
                result.account().updateAppleToken(refreshToken, verified.clientId());
            }
        }
        return issue(result.user());
    }

    /** 웹 리다이렉트 로그인 후 받은 일회용 코드를 토큰으로 바꾼다 */
    @Transactional  // 리프레시 토큰을 저장하므로 쓰기 트랜잭션
    public LoginResponse exchange(String code) {
        Long userId = loginCodeStore.consume(code);
        if (userId == null) throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
        Users user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return issue(user);
    }

    private LoginResponse issue(Users user) {
        TokenResponse tokens = refreshTokenService.issue(user.getUserId(), user.getLoginId());
        return LoginResponse.of(user.getUserId(), user.getLoginId(), user.getNickname(),
                tokens.accessToken(), tokens.refreshToken());
    }
}
