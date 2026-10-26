package com.untitles.domain.post.service;

import com.untitles.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜰 때 한 번, 체크리스트 항목 표시가 빠진 채 저장된 글을 고친다. (여러 번 돌아도 안전)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChecklistContentRepair implements ApplicationRunner {

    private final PostRepository postRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int fixed = postRepository.repairChecklistItems();
            if (fixed > 0) {
                log.info("체크리스트 항목 표시를 복구한 노트: {}개", fixed);
            }
        } catch (Exception e) {
            log.warn("체크리스트 복구 실패", e);
        }
    }
}
