package com.breathAI.ttobagi_server.domain.auth;

import com.breathAI.ttobagi_server.domain.auth.dto.PasswordResetConfirmRequest;
import com.breathAI.ttobagi_server.domain.auth.dto.PasswordResetRequest;
import com.breathAI.ttobagi_server.domain.auth.entity.PasswordResetToken;
import com.breathAI.ttobagi_server.domain.auth.entity.User;
import com.breathAI.ttobagi_server.domain.auth.repository.PasswordResetTokenRepository;
import com.breathAI.ttobagi_server.domain.auth.repository.UserRepository;
import com.breathAI.ttobagi_server.domain.auth.service.AuthService;
import com.breathAI.ttobagi_server.global.exception.CustomException;
import com.breathAI.ttobagi_server.global.exception.ErrorCode;
import com.breathAI.ttobagi_server.global.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

// 비밀번호 재설정 6자리 인증 코드 검증
// 실제 DB에 연결해 실행하지만 테스트가 끝나면 모두 롤백되고, 메일은 실제로 보내지 않는다
@SpringBootTest
@Transactional
class PasswordResetCodeTest {

    private static final String EMAIL = "reset-test@seoulmetro.co.kr";
    private static final String OTHER_EMAIL = "reset-other@seoulmetro.co.kr";
    private static final String OLD_PASSWORD = "oldpass1!";
    private static final String NEW_PASSWORD = "newpass1!";

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Validator validator;
    @Autowired private EntityManager em;

    @MockitoBean private EmailService emailService;

    private User user;

    @BeforeEach
    void setUp() {
        user = saveUser(EMAIL);
    }

    @Test
    @DisplayName("인증 코드는 영문 대소문자와 숫자로 된 6자리이고 메일로 발송된다")
    void codeIsSixAlphanumericCharacters() {
        for (int i = 0; i < 50; i++) {
            String code = requestCode(EMAIL);
            assertTrue(code.matches("^[A-Za-z0-9]{6}$"), code);
            // 서로 헷갈리는 글자는 쓰지 않는다
            assertFalse(code.matches(".*[0O1lI].*"), code);
            assertEquals(code, tokenRepository.findByUser(user).orElseThrow().getToken());
        }
    }

    @Test
    @DisplayName("이메일과 코드가 맞으면 비밀번호가 바뀌고, 같은 코드는 다시 쓸 수 없다")
    void correctCodeChangesPasswordOnce() {
        String code = requestCode(EMAIL);

        authService.confirmResetPassword(confirm(EMAIL, code, NEW_PASSWORD));
        em.flush();
        em.clear();

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, userRepository.findByEmail(EMAIL).orElseThrow().getPassword()));
        assertError(ErrorCode.ALREADY_USED_TOKEN, EMAIL, code);
    }

    @Test
    @DisplayName("대소문자가 다르면 틀린 코드로 처리한다")
    void codeIsCaseSensitive() {
        String code = requestCodeWithLetter();
        String swapped = swapCase(code);

        assertError(ErrorCode.INVALID_RESET_CODE, EMAIL, swapped);
        assertEquals(1, tokenRepository.findByUser(user).orElseThrow().getAttemptCount());
        assertTrue(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));

        // 원래 코드는 그대로 쓸 수 있다
        authService.confirmResetPassword(confirm(EMAIL, code, NEW_PASSWORD));
    }

    @Test
    @DisplayName("다섯 번 틀리면 맞는 코드를 넣어도 쓸 수 없다")
    void codeIsLockedAfterFiveFailures() {
        String code = requestCodeWithLetter();
        String wrong = swapCase(code);

        for (int i = 0; i < 5; i++) {
            assertError(ErrorCode.INVALID_RESET_CODE, EMAIL, wrong);
        }
        assertError(ErrorCode.RESET_CODE_ATTEMPTS_EXCEEDED, EMAIL, code);
        assertTrue(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));

        // 다시 발급받으면 새 코드로 재설정할 수 있다
        String reissued = requestCode(EMAIL);
        authService.confirmResetPassword(confirm(EMAIL, reissued, NEW_PASSWORD));
    }

    @Test
    @DisplayName("다시 발급받으면 이전 코드는 쓸 수 없다")
    void reissueInvalidatesPreviousCode() {
        String first = requestCode(EMAIL);
        String second = requestCode(EMAIL);
        // 드물게 같은 코드가 다시 나오면 비교할 수 없다
        org.junit.jupiter.api.Assumptions.assumeFalse(first.equals(second));

        assertError(ErrorCode.INVALID_RESET_CODE, EMAIL, first);
        authService.confirmResetPassword(confirm(EMAIL, second, NEW_PASSWORD));
    }

    @Test
    @DisplayName("다른 사용자의 코드나 없는 이메일로는 재설정할 수 없다")
    void codeOnlyWorksForItsOwner() {
        saveUser(OTHER_EMAIL);
        String otherCode = requestCode(OTHER_EMAIL);
        requestCode(EMAIL);

        // 다른 사용자에게 발급된 코드
        assertError(ErrorCode.INVALID_RESET_CODE, EMAIL, otherCode);
        // 가입하지 않은 이메일, 코드를 발급받지 않은 이메일도 같은 응답
        assertError(ErrorCode.INVALID_RESET_CODE, "nobody@seoulmetro.co.kr", otherCode);
        tokenRepository.deleteByUser(user);
        em.flush();
        assertError(ErrorCode.INVALID_RESET_CODE, EMAIL, otherCode);
    }

    @Test
    @DisplayName("만료된 코드는 쓸 수 없고 삭제된다")
    void expiredCodeIsRejected() {
        tokenRepository.save(PasswordResetToken.builder().token("Ab12Cd").user(user).expiryMinutes(-1).build());
        em.flush();

        assertError(ErrorCode.EXPIRED_RESET_CODE, EMAIL, "Ab12Cd");
        em.flush();
        assertTrue(tokenRepository.findByUser(user).isEmpty());
    }

    @Test
    @DisplayName("요청 형식: 이메일은 필수이고 코드는 영문·숫자 6자리만 허용한다")
    void requestValidation() {
        assertTrue(validator.validate(confirm(EMAIL, "aB3xY9", NEW_PASSWORD)).isEmpty());
        assertFalse(validator.validate(confirm(null, "aB3xY9", NEW_PASSWORD)).isEmpty());
        assertFalse(validator.validate(confirm(EMAIL, "aB3xY", NEW_PASSWORD)).isEmpty());
        assertFalse(validator.validate(confirm(EMAIL, "aB3xY9z", NEW_PASSWORD)).isEmpty());
        assertFalse(validator.validate(confirm(EMAIL, "aB3-Y9", NEW_PASSWORD)).isEmpty());
        assertFalse(validator.validate(confirm(EMAIL, "3f2a1b4c-0000-4000-8000-000000000000", NEW_PASSWORD)).isEmpty());
    }

    // 재설정을 요청하고 메일로 나간 코드를 돌려준다
    private String requestCode(String email) {
        authService.resetPassword(objectMapper.convertValue(Map.of("email", email), PasswordResetRequest.class));
        em.flush();
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendPasswordResetEmail(eq(email), code.capture());
        return code.getValue();
    }

    // 대소문자를 바꿔 볼 수 있도록 영문자가 든 코드가 나올 때까지 다시 받는다
    private String requestCodeWithLetter() {
        String code = requestCode(EMAIL);
        while (code.chars().noneMatch(Character::isLetter)) {
            code = requestCode(EMAIL);
        }
        return code;
    }

    private String swapCase(String code) {
        StringBuilder sb = new StringBuilder();
        for (char c : code.toCharArray()) {
            sb.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private PasswordResetConfirmRequest confirm(String email, String code, String newPassword) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("token", code);
        body.put("newPassword", newPassword);
        return objectMapper.convertValue(body, PasswordResetConfirmRequest.class);
    }

    private void assertError(ErrorCode expected, String email, String code) {
        CustomException ex = assertThrows(CustomException.class,
                () -> authService.confirmResetPassword(confirm(email, code, NEW_PASSWORD)));
        assertEquals(expected, ex.getErrorCode());
    }

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(OLD_PASSWORD))
                .role(User.Role.USER)
                .build());
    }
}
