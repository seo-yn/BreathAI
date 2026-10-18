package com.breathAI.ttobagi_server.integration;

import com.breathAI.ttobagi_server.domain.faq.entity.Faq;
import com.breathAI.ttobagi_server.domain.faq.repository.FaqRepository;
import com.breathAI.ttobagi_server.global.service.EmailService;
import com.breathAI.ttobagi_server.global.util.WebClientUtil;
import com.jayway.jsonpath.JsonPath;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 핵심 API 흐름 통합 시험 (인증·권한, 분석 업로드·콜백, FAQ)
// 테스트 전용 DB(Testcontainers)에서 실행하고, AI 호출과 메일 발송은 가짜로 바꾼다
// 업로드 후 AI 호출이 커밋 뒤에 일어나므로 트랜잭션 롤백을 쓰지 않고, 테스트마다 다른 계정·파일을 쓴다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    private static final String ADMIN_CODE = "test-admin-code";
    private static final String CALLBACK_TOKEN = "test-callback-token";
    private static final String PASSWORD = "qatest1!";

    @Autowired private MockMvc mvc;
    @Autowired private FaqRepository faqRepository;

    @MockitoBean private WebClientUtil webClientUtil;
    @MockitoBean private EmailService emailService;

    // ===== 인증·권한 =====

    @Test
    @DisplayName("관리자 코드로 가입하면 로그인 후 ADMIN으로 조회된다")
    void signupLoginAndMe() throws Exception {
        String token = adminToken();

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("토큰이 없거나 이상하면 401, 로그인 필요 메시지를 준다")
    void unauthenticatedRequestsReturn401() throws Exception {
        for (String path : List.of("/api/v1/auth/me", "/api/v1/faq", "/api/v1/dashboard/analyze/history")) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        }
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자는 관리자 기능에서 403을 받는다")
    void userIsForbiddenFromAdminFeatures() throws Exception {
        String user = userToken();
        Faq faq = saveFaq();

        mvc.perform(multipart("/api/v1/dashboard/analyze/upload").file(csv("user.csv"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(patch("/api/v1/faq/" + faq.getFaqId()), updateBody("수정"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/faq/" + faq.getFaqId()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("리프레시 토큰으로 재발급하고, 잘못된 토큰은 401이다")
    void reissueToken() throws Exception {
        String email = newEmail("reissue");
        signup(email, null);
        String refresh = JsonPath.read(login(email, PASSWORD), "$.data.refreshToken");

        MvcResult result = mvc.perform(withJson(post("/api/v1/auth/reissue"), "{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String access = JsonPath.read(body(result), "$.data.accessToken");
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isOk());

        mvc.perform(withJson(post("/api/v1/auth/reissue"), "{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호 재설정: 메일 코드로만 바꿀 수 있고 같은 코드는 다시 못 쓴다")
    void passwordResetWithMailedCode() throws Exception {
        String email = newEmail("reset");
        signup(email, null);

        mvc.perform(withJson(post("/api/v1/auth/password/reset"), "{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(email), code.capture());

        String newPassword = "qatest9!";
        mvc.perform(withJson(post("/api/v1/auth/password/reset/confirm"), confirmBody(email, "ZZZZZ2", newPassword)))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(post("/api/v1/auth/password/reset/confirm"), confirmBody(email, code.getValue(), newPassword)))
                .andExpect(status().isOk());

        login(email, newPassword);
        mvc.perform(withJson(post("/api/v1/auth/login"), loginBody(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(withJson(post("/api/v1/auth/password/reset/confirm"), confirmBody(email, code.getValue(), "qatest8!")))
                .andExpect(status().isBadRequest());
    }

    // ===== 분석 업로드·콜백 =====

    @Test
    @DisplayName("CSV 업로드는 PENDING으로 시작하고 같은 파일을 AI에 그대로 넘긴다")
    void csvUploadDispatchesSameFileToAi() throws Exception {
        MockMultipartFile file = csv("data.csv");
        Long analysisId = upload(adminToken(), file);

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(webClientUtil, timeout(5000)).sendPipeline(anyString(), eq(analysisId), bytes.capture(), eq("data.csv"),
                anyBoolean(), anyBoolean(), anyString(), anyString(), any());
        assertThat(bytes.getValue()).isEqualTo(file.getBytes());
    }

    @Test
    @DisplayName("XLSX 업로드는 CSV로 바꿔 AI에 넘긴다")
    void xlsxUploadIsConvertedToCsv() throws Exception {
        Long analysisId = upload(adminToken(), xlsx("data.xlsx"));

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(webClientUtil, timeout(5000)).sendPipeline(anyString(), eq(analysisId), bytes.capture(), eq("data.csv"),
                anyBoolean(), anyBoolean(), anyString(), anyString(), any());
        assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8)).startsWith("번호,질문,날짜");
    }

    @Test
    @DisplayName("지원하지 않는 형식은 400이다")
    void unsupportedFormatIsRejected() throws Exception {
        MockMultipartFile txt = new MockMultipartFile("file", "data.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/v1/dashboard/analyze/upload").file(txt)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("콜백은 토큰이 맞을 때만 받고, 끝난 분석에 다시 온 콜백은 저장하지 않는다")
    void callbackRequiresTokenAndIgnoresDuplicates() throws Exception {
        String admin = adminToken();
        Long analysisId = upload(admin, csv("callback.csv"));
        String path = "/api/v1/dashboard/analyze/callback/" + analysisId;

        mvc.perform(withJson(post(path), completedBody())).andExpect(status().isUnauthorized());
        mvc.perform(withJson(post(path), completedBody()).header(HttpHeaders.AUTHORIZATION, "Bearer wrong"))
                .andExpect(status().isUnauthorized());

        callback(path, completedBody());
        assertStatus(admin, analysisId, "COMPLETED");
        assertRecommendationCount(admin, analysisId, 1);

        // 같은 완료 콜백이 다시 오거나, 완료 후 실패 콜백이 와도 결과는 그대로다
        callback(path, completedBody());
        callback(path, "{\"status\":\"FAILED\",\"errorMessage\":\"late\"}");
        assertStatus(admin, analysisId, "COMPLETED");
        assertRecommendationCount(admin, analysisId, 1);
    }

    @Test
    @DisplayName("실패 콜백을 받으면 FAIL로 바뀐다")
    void failedCallbackMarksFail() throws Exception {
        String admin = adminToken();
        Long analysisId = upload(admin, csv("fail.csv"));

        callback("/api/v1/dashboard/analyze/callback/" + analysisId, "{\"status\":\"FAILED\",\"errorMessage\":\"boom\"}");
        assertStatus(admin, analysisId, "FAIL");
    }

    // ===== FAQ =====

    @Test
    @DisplayName("AI 후보를 FAQ로 반영하고, 같은 후보를 다시 반영하면 409이다")
    void applyCandidateOnce() throws Exception {
        String admin = adminToken();
        Long analysisId = upload(admin, csv("apply.csv"));
        callback("/api/v1/dashboard/analyze/callback/" + analysisId, completedBody());

        String recommendations = body(mvc.perform(get("/api/v1/faq/recommendations/" + analysisId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andReturn());
        Number clusterId = JsonPath.read(recommendations, "$.data.recommendations[0].clusterId");
        Number candidateId = JsonPath.read(recommendations, "$.data.recommendations[0].candidateId");
        String apply = "{\"analysisId\":" + analysisId + ",\"clusterId\":" + clusterId + ",\"candidateId\":" + candidateId
                + ",\"finalQuestion\":\"반영 질문 " + UUID.randomUUID() + "\",\"finalAnswer\":\"반영 답변\""
                + ",\"keywords\":[\"환승\"],\"isManualPatchConfirmed\":true}";

        mvc.perform(withJson(post("/api/v1/faq/apply"), apply).header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/faq/apply"), apply).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedFaqId").isNumber());
        mvc.perform(withJson(post("/api/v1/faq/apply"), apply).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("관리자가 FAQ를 수정·삭제하면 이력이 남는다")
    void adminUpdatesAndDeletesFaq() throws Exception {
        String admin = adminToken();
        Faq faq = saveFaq();

        mvc.perform(withJson(patch("/api/v1/faq/" + faq.getFaqId()), updateBody("바뀐 답변"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/faq/" + faq.getFaqId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("바뀐 답변"));

        mvc.perform(delete("/api/v1/faq/" + faq.getFaqId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/faq/history?editType=DELETE").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.histories[?(@.faqId == " + faq.getFaqId() + ")]").isNotEmpty());
    }

    @Test
    @DisplayName("FAQ 다운로드는 BOM이 붙은 CSV를 주고 버전 목록에 남는다")
    void downloadCreatesVersion() throws Exception {
        String admin = adminToken();
        saveFaq();

        MvcResult result = mvc.perform(get("/api/v1/faq/download").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("faq_v")))
                .andReturn();
        byte[] csv = result.getResponse().getContentAsByteArray();
        assertThat(csv).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        mvc.perform(get("/api/v1/faq/versions").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    // ===== 도우미 =====

    private String adminToken() throws Exception {
        String email = newEmail("admin");
        signup(email, ADMIN_CODE);
        return JsonPath.read(login(email, PASSWORD), "$.data.accessToken");
    }

    private String userToken() throws Exception {
        String email = newEmail("user");
        signup(email, null);
        return JsonPath.read(login(email, PASSWORD), "$.data.accessToken");
    }

    private void signup(String email, String adminCode) throws Exception {
        String code = adminCode == null ? "" : ",\"adminCode\":\"" + adminCode + "\"";
        mvc.perform(withJson(post("/api/v1/auth/signup"), "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"" + code + "}"))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        return body(mvc.perform(withJson(post("/api/v1/auth/login"), loginBody(email, password)))
                .andExpect(status().isOk()).andReturn());
    }

    private Long upload(String token, MockMultipartFile file) throws Exception {
        String response = body(mvc.perform(multipart("/api/v1/dashboard/analyze/upload").file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn());
        return ((Number) JsonPath.read(response, "$.data.analysisId")).longValue();
    }

    private void callback(String path, String json) throws Exception {
        mvc.perform(withJson(post(path), json).header(HttpHeaders.AUTHORIZATION, "Bearer " + CALLBACK_TOKEN))
                .andExpect(status().isOk());
    }

    private void assertStatus(String token, Long analysisId, String expected) throws Exception {
        mvc.perform(get("/api/v1/dashboard/analyze/" + analysisId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(expected));
    }

    private void assertRecommendationCount(String token, Long analysisId, int expected) throws Exception {
        mvc.perform(get("/api/v1/faq/recommendations/" + analysisId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations.length()").value(expected));
    }

    private Faq saveFaq() {
        return faqRepository.save(Faq.builder()
                .question("통합테스트 질문 " + UUID.randomUUID())
                .answer("통합테스트 답변")
                .qType(176)
                .build());
    }

    // 파일 내용이 매번 달라야 같은 파일 재업로드로 처리되지 않는다
    private MockMultipartFile csv(String name) {
        String content = "번호,질문,날짜\n1,환승은 어떻게 하나요 " + UUID.randomUUID() + ",2026-08-01\n";
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile xlsx(String name) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            String[][] rows = {{"번호", "질문", "날짜"}, {"1", "엑셀 질문 " + UUID.randomUUID(), "2026-08-01"}};
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < rows[i].length; j++) {
                    row.createCell(j).setCellValue(rows[i][j]);
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", name,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    // AI 담당이 준 완료 콜백 예시
    private String completedBody() {
        return "{\"status\":\"COMPLETED\",\"clusteringView\":{\"clusterNames\":[{\"clusterLabel\":3,\"name\":\"환승 문의\","
                + "\"topKeywords\":[\"환승\"],\"size\":5}],\"points\":[]},\"faqCandidates\":[{\"clusterLabel\":3,"
                + "\"candidateType\":\"NEW\",\"standardQuestion\":\"환승은 어떻게 하나요?\",\"answerDraft\":\"안내 표지를 따라 이동하세요.\","
                + "\"qType\":null,\"category\":\"이용안내\",\"similarQuestions\":[\"환승 문의\"],\"occurrenceCount\":5,"
                + "\"representativeKeywords\":[\"환승\"],\"synonyms\":[{\"text\":\"갈아타기\",\"type\":\"SYNONYM\"}],\"matchedFaqs\":[]}]}";
    }

    private String updateBody(String answer) {
        return "{\"qType\":176,\"standardQuestion\":\"통합테스트 수정 질문\",\"answer\":\"" + answer + "\","
                + "\"keywords\":[\"시험\"],\"editReason\":\"통합테스트\"}";
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String confirmBody(String email, String token, String newPassword) {
        return "{\"email\":\"" + email + "\",\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}";
    }

    private MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, String json) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json.getBytes(StandardCharsets.UTF_8));
    }

    private String newEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@seoulmetro.co.kr";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
