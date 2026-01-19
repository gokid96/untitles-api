package com.untitles.domain.email.dto.request;

public record EmailVerifyRequest(String email, String code) {}