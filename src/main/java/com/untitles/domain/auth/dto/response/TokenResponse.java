package com.untitles.domain.auth.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {}
