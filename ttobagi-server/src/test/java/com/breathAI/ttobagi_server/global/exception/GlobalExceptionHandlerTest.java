package com.breathAI.ttobagi_server.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 잘못된 요청이 500이 아니라 원인에 맞는 상태 코드로 응답되는지 검증
// 모두 컨트롤러에 들어가기 전에 걸러지는 요청이라 DB에는 쓰지 않는다
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("숫자 자리에 글자가 오면 400")
    void typeMismatchReturns400() throws Exception {
        expectError(get("/api/v1/faq/download").param("versionId", "abc"), 400);
        expectError(get("/api/v1/faq/abc"), 400);
        expectError(get("/api/v1/faq").param("page", "x"), 400);
    }

    @Test
    @DisplayName("날짜 형식이 틀리거나 없는 enum 값이면 400")
    void invalidDateOrEnumReturns400() throws Exception {
        expectError(get("/api/v1/dashboard/analyze/result").param("startDate", "2026/10/05"), 400);
        expectError(get("/api/v1/faq/history").param("editType", "XXX"), 400);
    }

    @Test
    @DisplayName("필수 파라미터나 파일이 없으면 400")
    void missingParameterReturns400() throws Exception {
        expectError(get("/api/v1/dashboard/usage").param("year", "2026"), 400);
        expectError(multipart("/api/v1/dashboard/analyze/upload").param("periodStartDate", "2026-01-01"), 400);
    }

    @Test
    @DisplayName("없는 경로는 404")
    void unknownPathReturns404() throws Exception {
        expectError(get("/api/v1/nothing"), 404);
    }

    @Test
    @DisplayName("허용되지 않은 메서드는 405")
    void unsupportedMethodReturns405() throws Exception {
        expectError(put("/api/v1/faq/2"), 405);
    }

    @Test
    @DisplayName("JSON이 아닌 본문 형식은 415")
    void unsupportedMediaTypeReturns415() throws Exception {
        expectError(patch("/api/v1/faq/2").contentType(MediaType.TEXT_PLAIN).content("hello"), 415);
    }

    @Test
    @DisplayName("깨진 JSON은 400")
    void brokenJsonReturns400() throws Exception {
        expectError(patch("/api/v1/faq/2").contentType(MediaType.APPLICATION_JSON).content("{bad"), 400);
    }

    // 상태 코드와 공통 응답 형식을 함께 확인한다
    private ResultActions expectError(RequestBuilder request, int expectedStatus) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
