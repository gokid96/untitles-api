package com.untitles.domain.user.repository;

import com.untitles.domain.user.entity.Users;
import com.untitles.support.H2JpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@H2JpaTest
class UserRepositoryTest {

    @Autowired UserRepository userRepository;

    private Long meId;

    @BeforeEach
    void setUp() {
        meId = userRepository.save(Users.createLocal("me@example.com", "me", "pw", "나")).getUserId();
        userRepository.save(Users.createLocal("alice@example.com", "alice", "pw", "앨리스"));
        userRepository.save(Users.createLocal("bob@example.com", "bob", "pw", "bob_100%"));
        userRepository.save(Users.createLocal(null, "noemail", "pw", "이메일없음"));
    }

    private List<String> search(String query, String likeQuery) {
        return userRepository.searchForInvite(query, likeQuery, meId, PageRequest.of(0, 10))
                .stream().map(Users::getNickname).toList();
    }

    @Test
    @DisplayName("이메일은 정확히 일치할 때만 찾는다")
    void emailExactOnly() {
        assertThat(search("alice@example.com", "alice@example.com")).containsExactly("앨리스");
        assertThat(search("example.com", "example.com")).isEmpty();
    }

    @Test
    @DisplayName("닉네임은 부분 일치로 찾고 본인은 제외한다")
    void nicknamePartial() {
        assertThat(search("앨리", "앨리")).containsExactly("앨리스");
        assertThat(search("나", "나")).isEmpty();
    }

    @Test
    @DisplayName("% 같은 특수문자는 그대로의 글자로 검색한다")
    void wildcardEscaped() {
        assertThat(search("%", "!%")).containsExactly("bob_100%");
        assertThat(search("_", "!_")).containsExactly("bob_100%");
    }

    @Test
    @DisplayName("결과 수를 DB에서 제한한다")
    void limited() {
        assertThat(userRepository.searchForInvite("x", "", meId, PageRequest.of(0, 1))).hasSize(1);
    }
}
