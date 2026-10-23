package com.untitles.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new RateLimitConfig().rateLimitBuckets());
    }

    private int login(String remoteAddr, String xForwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (xForwardedFor != null) {
            request.addHeader("X-Forwarded-For", xForwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    @DisplayName("로그인은 같은 IP에서 분당 10회를 넘으면 429")
    void loginLimit() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(login("203.0.113.1", null)).isEqualTo(200);
        }
        assertThat(login("203.0.113.1", null)).isEqualTo(429);
    }

    @Test
    @DisplayName("X-Forwarded-For를 바꿔 보내도 제한을 우회할 수 없다")
    void spoofedHeaderDoesNotBypass() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(login("203.0.113.1", "198.51.100." + i)).isEqualTo(200);
        }
        assertThat(login("203.0.113.1", "198.51.100.99")).isEqualTo(429);
    }

    @Test
    @DisplayName("다른 IP는 별도로 제한한다")
    void separateBucketPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            login("203.0.113.1", null);
        }
        assertThat(login("203.0.113.2", null)).isEqualTo(200);
    }
}
