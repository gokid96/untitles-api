package com.untitles.domain.user.repository;

import com.untitles.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByEmail(String email);
    Optional<Users> findByLoginId(String loginId);
    Optional<Users> findByNickname(String nickname);
    boolean existsByEmail(String email);
    boolean existsByLoginId(String loginId);
    boolean existsByNickname(String nickname);
    
    // 이메일 또는 닉네임으로 사용자 검색 (부분 일치, 대소문자 무시)
    @Query("SELECT u FROM Users u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Users> findByEmailOrNicknameContaining(@Param("query") String query);
}