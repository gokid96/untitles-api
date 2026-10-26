package com.untitles.domain.auth.repository;

import com.untitles.domain.auth.entity.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

    Optional<PasswordReset> findByUserId(Long userId);

    /**
     * 시도 횟수를 원자적으로 1 올린다. 만료됐거나 횟수를 다 쓴 경우 0을 돌려준다.
     * (동시에 여러 번 입력해도 최대 횟수를 넘겨 시도할 수 없다)
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PasswordReset r SET r.attemptCount = r.attemptCount + 1 " +
            "WHERE r.userId = :userId AND r.attemptCount < :maxAttempts AND r.expiresAt > :now")
    int useAttempt(@Param("userId") Long userId, @Param("maxAttempts") int maxAttempts,
                   @Param("now") java.time.LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PasswordReset r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
