package com.untitles.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private CorsConfiguration config() {
        SecurityConfig securityConfig = new SecurityConfig(null, null, null, null, null, null);
        return securityConfig.corsConfigurationSource().getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/users/me"));
    }

    @Test
    @DisplayName("스토어 앱 웹뷰 출처(안드로이드·iOS)를 허용한다")
    void allowsAppOrigins() {
        assertThat(config().checkOrigin("https://localhost")).isEqualTo("https://localhost");
        assertThat(config().checkOrigin("capacitor://localhost")).isEqualTo("capacitor://localhost");
    }

    @Test
    @DisplayName("모르는 출처는 허용하지 않는다")
    void rejectsUnknownOrigin() {
        assertThat(config().checkOrigin("https://evil.example.com")).isNull();
    }
}
