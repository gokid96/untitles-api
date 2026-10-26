package com.untitles.domain.email.repository;

import com.untitles.domain.email.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByEmailAndVerifiedFalse(String email);
    Optional<EmailVerification> findByEmailAndVerifiedTrue(String email);
    
    @Modifying
    void deleteByEmail(String email);

    /**
     * 시도 횟수를 원자적으로 1 올린다. 횟수를 다 썼으면 0을 돌려준다.
     */
    @Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE EmailVerification v SET v.attemptCount = v.attemptCount + 1 " +
            "WHERE v.id = :id AND v.attemptCount < :maxAttempts")
    int useAttempt(@org.springframework.data.repository.query.Param("id") Long id,
                   @org.springframework.data.repository.query.Param("maxAttempts") int maxAttempts);
}
