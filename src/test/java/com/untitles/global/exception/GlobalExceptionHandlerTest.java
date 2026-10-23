package com.untitles.global.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class SampleController {
        record Body(String name) {}

        @GetMapping("/items/{id}")
        String get(@PathVariable Long id) { return "ok"; }

        @GetMapping("/search")
        String search(@RequestParam String query) { return "ok"; }

        @PostMapping("/items")
        String create(@RequestBody Body body) { return "ok"; }

        @GetMapping("/boom")
        String boom() { throw new RuntimeException("boom"); }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("없는 경로 예외는 404")
    void notFound() {
        var response = new GlobalExceptionHandler()
                .handleNotFound(new NoResourceFoundException(HttpMethod.GET, "api/v1/public"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("숫자 자리에 문자를 넣으면 400")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/items/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 400")
    void missingParam() throws Exception {
        mockMvc.perform(get("/search")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("깨진 JSON은 400")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(delete("/items/1")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("예상하지 못한 예외는 그대로 500")
    void unexpected() throws Exception {
        mockMvc.perform(get("/boom")).andExpect(status().isInternalServerError());
    }
}
