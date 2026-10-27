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
    private final PendingLinkStore pendingLinkStore;

    @Transactional
    public LoginResponse loginWithGoogle(String idToken) {
        SocialLoginService.Result result = socialLoginService.login(idTokenVerifier.verifyGoogle(idToken));
        return issue(result.user());
    }

    @Transactional
    public LoginResponse loginWithApple(String idToken, String authorizationCode, String rawNonce, String name) {
        return issue(appleUser(idToken, authorizationCode, rawNonce, name));
    }

    /**
     * 웹 애플 로그인(리다이렉트 방식): 애플이 우리 서버로 보낸 결과를 확인하고, 웹에 넘길 일회용 코드를 만든다.
     * (웹 리다이렉트는 브라우저 상태를 잃으므로 nonce 대신 웹이 state로 위조 요청을 막는다)
     */
    @Transactional
    public String loginWithAppleRedirect(String idToken, String authorizationCode, String name) {
        return loginCodeStore.issue(appleUser(idToken, authorizationCode, null, name).getUserId());
    }

    private Users appleUser(String idToken, String authorizationCode, String rawNonce, String name) {
        IdTokenVerifier.VerifiedApple verified = idTokenVerifier.verifyApple(idToken, rawNonce, name);
        SocialLoginService.Result result;
        try {
            result = socialLoginService.login(verified.profile());
        } catch (SocialLinkRequiredException e) {
            // 나중에 연결될 때 탈퇴 처리에 쓸 애플 토큰도 함께 기억해 둔다 (인가 코드는 한 번만 쓸 수 있음)
            String refreshToken = appleAuthClient.exchangeCode(authorizationCode, verified.clientId());
            if (refreshToken != null) pendingLinkStore.attachAppleToken(e.getLinkToken(), refreshToken, verified.clientId());
            throw e;
        }

        // 탈퇴 때 애플 연결을 끊을 수 있도록 리프레시 토큰을 받아 둔다 (처음 한 번, 또는 아직 없으면)
        if (result.account().getAppleRefreshToken() == null) {
            String refreshToken = appleAuthClient.exchangeCode(authorizationCode, verified.clientId());
            if (refreshToken != null) {
                result.account().updateAppleToken(refreshToken, verified.clientId());
            }
        }
        return result.user();
    }

    /** 웹 리다이렉트 로그인 후 받은 일회용 코드를 토큰으로 바꾼다 */
    @Transactional  // 리프레시 토큰을 저장하므로 쓰기 트랜잭션
    public LoginResponse exchange(String code) {
        Long userId = loginCodeStore.consume(code);
        if (userId == null) throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
        Users user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return issue(user);
    }

    public record PendingLinkInfo(String email, String provider) {
    }

    /** 연결 요청 정보 (로그인 화면에 이메일을 채우고 어떤 계정이 연결되는지 보여주기 위해) */
    public PendingLinkInfo pendingLink(String token) {
        PendingLinkStore.PendingLink link = pendingLinkStore.peek(token);
        if (link == null) throw new BusinessException(ErrorCode.SOCIAL_LINK_EXPIRED);
        Users user = userRepository.findById(link.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_LINK_EXPIRED));
        return new PendingLinkInfo(user.getEmail(), link.provider().name());
    }

    private LoginResponse issue(Users user) {
        TokenResponse tokens = refreshTokenService.issue(user.getUserId(), user.getLoginId());
        return LoginResponse.of(user.getUserId(), user.getLoginId(), user.getNickname(),
                tokens.accessToken(), tokens.refreshToken());
    }
}
