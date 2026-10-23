package com.untitles.domain.auth.controller;

import com.untitles.domain.auth.service.AuthService;
import com.untitles.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerValidationTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void signup(String body, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("규칙에 맞지 않는 비밀번호로는 가입할 수 없다")
    void weakPasswordRejected() throws Exception {
        signup("""
                {"email":"a@example.com","loginId":"tester","password":"1","nickname":"테스터"}
                """, 400);
        verify(authService, never()).signup(any());
    }

    @Test
    @DisplayName("아이디 형식이 틀리면 가입할 수 없다")
    void invalidLoginIdRejected() throws Exception {
        signup("""
                {"email":"a@example.com","loginId":"A!","password":"abcd1234!","nickname":"테스터"}
                """, 400);
        verify(authService, never()).signup(any());
    }

    @Test
    @DisplayName("올바른 요청은 서비스로 전달된다")
    void validRequestPasses() throws Exception {
        signup("""
                {"email":"a@example.com","loginId":"tester","password":"abcd1234!","nickname":"테스터"}
                """, 201);
        verify(authService).signup(any());
    }

    @Test
    @DisplayName("빈 로그인 요청은 400")
    void blankLoginRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("이메일 없이도 가입 요청이 검증을 통과한다")
    void signupWithoutEmailPasses() throws Exception {
        signup("""
                {"loginId":"tester","password":"abcd1234!","nickname":"테스터"}
                """, 201);
        verify(authService).signup(any());
    }

    @Test
    @DisplayName("이메일을 보냈다면 형식은 검사한다")
    void invalidEmailRejected() throws Exception {
        signup("""
                {"email":"not-an-email","loginId":"tester","password":"abcd1234!","nickname":"테스터"}
                """, 400);
    }
}
