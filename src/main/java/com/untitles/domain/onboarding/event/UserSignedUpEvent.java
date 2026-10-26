package com.untitles.domain.onboarding.event;

/**
 * 새 사용자가 가입하고 개인 워크스페이스가 만들어졌을 때 발행
 */
public record UserSignedUpEvent(Long userId, Long workspaceId) {
}
