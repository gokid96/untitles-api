package com.untitles.domain.user.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSearchResponseDTOTest {

    @Test
    @DisplayName("검색 결과의 이메일은 앞 두 글자만 보인다")
    void masksEmail() {
        assertThat(UserSearchResponseDTO.maskEmail("alice@example.com")).isEqualTo("al***@example.com");
        assertThat(UserSearchResponseDTO.maskEmail("a@example.com")).isEqualTo("a***@example.com");
        assertThat(UserSearchResponseDTO.maskEmail(null)).isNull();
    }
}
