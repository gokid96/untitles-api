package com.untitles.domain.auth.social;

import com.untitles.domain.auth.entity.SocialAccount;
import com.untitles.domain.auth.repository.SocialAccountRepository;
import com.untitles.domain.user.entity.AuthProvider;
import com.untitles.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예전에는 users 테이블에 소셜 제공자를 하나만 적어 두었다. 서버가 뜰 때 social_account로 옮긴다 (여러 번 돌아도 안전).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacySocialAccountMigration implements ApplicationRunner {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            int moved = 0;
            for (var user : userRepository.findAll()) {
                AuthProvider provider = user.getProvider();
                String subject = user.getProviderId();
                if (provider == null || provider == AuthProvider.LOCAL || subject == null || subject.isBlank()) continue;
                if (socialAccountRepository.findByProviderAndSubject(provider, subject).isPresent()) continue;
                socialAccountRepository.save(SocialAccount.of(user.getUserId(), provider, subject, user.getEmail()));
                moved++;
            }
            if (moved > 0) log.info("예전 소셜 연결 {}건을 social_account로 옮겼습니다", moved);
        } catch (Exception e) {
            log.warn("예전 소셜 연결 이전 실패", e);
        }
    }
}
