package com.roadstudy;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;
import com.roadstudy.global.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("전역 예외 처리")
class GlobalExceptionHandlerTests {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @CsvSource({
        "INVALID_INPUT,400", "NOT_FOUND,404", "CONFLICT,409", "PAYLOAD_TOO_LARGE,413",
        "UNPROCESSABLE_CONTENT,422", "EXTERNAL_SERVICE_FAILURE,502", "EXTERNAL_SERVICE_TIMEOUT,504"
    })
    @DisplayName("실패 유형에 해당하는 HTTP 상태와 오류 코드를 반환한다")
    void mapsApplicationErrors(FailureType type, int statusCode) throws Exception {
        mvc.perform(get("/failure/{type}", type))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.code").value("STUDY_FAILURE"));
    }

    @Test
    @DisplayName("요청 검증 실패 시 400을 반환한다")
    void handlesValidation() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("업로드 허용 용량을 초과하면 413을 반환한다")
    void handlesUploadLimit() throws Exception {
        mvc.perform(get("/upload-limit"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("예상하지 못한 예외의 내부 메시지를 숨기고 500을 반환한다")
    void sanitizesUnexpectedErrors() throws Exception {
        mvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("서버에서 예상하지 못한 오류가 발생했습니다."));
    }

    @RestController
    static class TestController {
        @GetMapping("/failure/{type}")
        void fail(@PathVariable("type") FailureType type) {
            throw new StudyFailure(type);
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Input input) {
        }

        @GetMapping("/upload-limit")
        void uploadLimit() {
            throw new MaxUploadSizeExceededException(1);
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalArgumentException("외부에 공개할 수 없는 오류 정보");
        }
    }

    record Input(@NotBlank String title) {
    }

    private static class StudyFailure extends ApplicationException {
        StudyFailure(FailureType type) {
            super("STUDY_FAILURE", "학습 요청을 처리할 수 없습니다.", type);
        }
    }
}
