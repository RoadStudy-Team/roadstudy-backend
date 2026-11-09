package com.roadstudy;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;
import com.roadstudy.global.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
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
        "INVALID_INPUT,400", "NOT_FOUND,404", "CONFLICT,409",
        "UNPROCESSABLE_CONTENT,422", "EXTERNAL_SERVICE_FAILURE,502", "EXTERNAL_SERVICE_TIMEOUT,504"
    })
    void mapsApplicationErrors(FailureType type, int statusCode) throws Exception {
        mvc.perform(get("/failure/{type}", type))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.code").value("STUDY_FAILURE"));
    }

    @Test
    void handlesValidation() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void handlesUploadLimit() throws Exception {
        mvc.perform(get("/upload-limit"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
    }

    @Test
    void sanitizesUnexpectedErrors() throws Exception {
        mvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
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
            throw new IllegalArgumentException("Private diagnostic detail");
        }
    }

    record Input(@NotBlank String title) {
    }

    private static class StudyFailure extends ApplicationException {
        StudyFailure(FailureType type) {
            super("STUDY_FAILURE", "Study request failed.", type);
        }
    }
}
