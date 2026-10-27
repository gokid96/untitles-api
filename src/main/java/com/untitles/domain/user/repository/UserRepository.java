package com.untitles.domain.user.repository;

import com.untitles.domain.user.entity.Users;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByEmail(String email);
    Optional<Users> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<Users> findByLoginId(String loginId);

    boolean existsByEmail(String email);
    boolean existsByLoginId(String loginId);
    boolean existsByNickname(String nickname);

    // 초대용 사용자 검색: 이메일은 정확히 일치, 닉네임은 부분 일치 (대소문자 무시, 본인 제외)
    // 이메일 부분 검색을 허용하면 전체 회원 이메일을 모을 수 있어 막는다
    @Query("SELECT u FROM Users u WHERE u.userId <> :excludeUserId AND ("
            + "LOWER(u.email) = LOWER(:query) "
            + "OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :likeQuery, '%')) ESCAPE '!') "
            + "ORDER BY u.nickname")
    List<Users> searchForInvite(@Param("query") String query,
                                @Param("likeQuery") String likeQuery,
                                @Param("excludeUserId") Long excludeUserId,
                                Pageable pageable);

}