package com.untitles.domain.auth.service;

import com.untitles.domain.auth.dto.response.TokenResponse;
import com.untitles.domain.auth.entity.RefreshToken;
import com.untitles.domain.auth.repository.RefreshTokenRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 리프레시 토큰 발급·교체·무효화. 발급한 토큰의 해시를 DB에 저장해 두고, 재발급 때마다 교체한다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    /**
     * 액세스·리프레시 토큰을 새로 발급하고 리프레시 토큰을 저장
     */
    @Transactional
    public TokenResponse issue(Long userId, String loginId) {
        String refreshToken = jwtProvider.createRefreshToken(userId, loginId);
        refreshTokenRepository.save(RefreshToken.of(userId, hash(refreshToken),
                LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpiration() / 1000)));
        return new TokenResponse(jwtProvider.createAccessToken(userId, loginId), refreshToken);
    }

    /**
     * 저장된 유효한 리프레시 토큰이면 지우고 사용자 ID를 돌려준다 (한 번 쓴 토큰은 다시 못 쓴다)
     */
    @Transactional
    public Long consume(String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || !jwtProvider.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (refreshTokenRepository.deleteByTokenHash(hash(refreshToken)) == 0) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);  // 로그아웃·교체·무효화된 토큰
        }
        return jwtProvider.getUserId(refreshToken);
    }

    /**
     * 로그아웃: 이 기기의 리프레시 토큰만 무효화
     */
    @Transactional
    public void revoke(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenRepository.deleteByTokenHash(hash(refreshToken));
        }
    }

    /**
     * 비밀번호 변경·탈퇴: 이 사용자의 모든 기기 로그인 무효화
     */
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void deleteExpired() {
        refreshTokenRepository.deleteExpired(LocalDateTime.now());
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
