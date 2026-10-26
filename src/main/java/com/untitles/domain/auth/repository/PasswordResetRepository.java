package com.untitles.domain.auth.repository;

import com.untitles.domain.auth.entity.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

    Optional<PasswordReset> findByUserId(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PasswordReset r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
