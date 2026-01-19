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
}
