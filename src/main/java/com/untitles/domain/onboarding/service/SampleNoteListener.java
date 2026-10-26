package com.untitles.domain.onboarding.service;

import com.untitles.domain.onboarding.event.UserSignedUpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 가입이 커밋된 뒤 예시 노트를 만든다. 실패해도 가입은 그대로 성공한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleNoteListener {

    private final SampleNoteService sampleNoteService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSignedUp(UserSignedUpEvent event) {
        try {
            sampleNoteService.createSampleNotes(event.userId(), event.workspaceId());
        } catch (Exception e) {
            log.warn("예시 노트 생성 실패: userId={}", event.userId(), e);
        }
    }
}
