package com.untitles.domain.auth.repository;

import com.untitles.domain.auth.entity.SocialAccount;
import com.untitles.domain.user.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndSubject(AuthProvider provider, String subject);

    List<SocialAccount> findAllByUserId(Long userId);

    boolean existsByUserIdAndProvider(Long userId, AuthProvider provider);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SocialAccount s WHERE s.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
