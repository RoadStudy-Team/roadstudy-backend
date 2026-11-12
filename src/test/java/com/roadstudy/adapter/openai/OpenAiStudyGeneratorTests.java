package com.roadstudy.adapter.openai;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstudy.application.study.StudyExplanation;
import com.roadstudy.application.study.exception.StudyExplanationException;
import com.roadstudy.global.exception.FailureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("OpenAI 학습 설명 생성 어댑터")
class OpenAiStudyGeneratorTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test/v1");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiStudyGenerator generator = new OpenAiStudyGenerator(builder.build(), mapper, "test-model");

    @Test
    @DisplayName("구조화 출력 요청을 보내고 세 필드를 파싱한다")
    void generatesStructuredExplanation() throws Exception {
        server.expect(requestTo("https://example.test/v1/responses"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.input").value("학습 자료"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andRespond(withSuccess(response("""
                        {"title":"제목","summary":"요약","explanationScript":"설명 대본"}
                        """), MediaType.APPLICATION_JSON));

        assertThat(generator.generate("학습 자료")).isEqualTo(new StudyExplanation("제목", "요약", "설명 대본"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{", "{}",
            "{\"title\":\"제목\",\"summary\":\"요약\",\"explanationScript\":\" \"}",
            "{\"title\":1,\"summary\":\"요약\",\"explanationScript\":\"대본\"}",
            "{\"title\":\"제목\",\"summary\":\"요약\",\"explanationScript\":\"대본\"} {}"
    })
    @DisplayName("깨진 JSON과 누락 또는 공백 필드를 외부 응답 오류로 처리한다")
    void rejectsInvalidOutput(String output) throws Exception {
        server.expect(requestTo("https://example.test/v1/responses"))
                .andRespond(withSuccess(response(output), MediaType.APPLICATION_JSON));

        assertFailure(FailureType.EXTERNAL_SERVICE_FAILURE, "INVALID_STUDY_EXPLANATION");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{",
            "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}"
    })
    @DisplayName("응답 본문이 손상됐거나 생성이 완료되지 않으면 결과를 거부한다")
    void rejectsInvalidResponse(String body) {
        server.expect(requestTo("https://example.test/v1/responses"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertFailure(FailureType.EXTERNAL_SERVICE_FAILURE, "INVALID_STUDY_EXPLANATION");
    }

    @Test
    @DisplayName("외부 API의 HTTP 오류를 서비스 호출 실패로 변환한다")
    void handlesUpstreamError() {
        server.expect(requestTo("https://example.test/v1/responses"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("upstream details"));

        assertFailure(FailureType.EXTERNAL_SERVICE_FAILURE, "STUDY_GENERATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 504})
    @DisplayName("외부 API가 반환한 타임아웃 상태를 시간 초과로 변환한다")
    void handlesUpstreamTimeoutStatus(int status) {
        server.expect(requestTo("https://example.test/v1/responses"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertFailure(FailureType.EXTERNAL_SERVICE_TIMEOUT, "STUDY_GENERATION_TIMEOUT");
    }

    @Test
    @DisplayName("외부 API 읽기 시간 초과를 타임아웃으로 변환한다")
    void handlesTimeout() {
        server.expect(requestTo("https://example.test/v1/responses"))
                .andRespond(withException(new HttpTimeoutException("timeout")));

        assertFailure(FailureType.EXTERNAL_SERVICE_TIMEOUT, "STUDY_GENERATION_TIMEOUT");
    }

    @Test
    @DisplayName("연결 거부를 타임아웃과 구분해 호출 실패로 처리한다")
    void handlesConnectionRefused() {
        server.expect(requestTo("https://example.test/v1/responses"))
                .andRespond(withException(new ConnectException("connection refused")));

        assertFailure(FailureType.EXTERNAL_SERVICE_FAILURE, "STUDY_GENERATION_FAILED");
    }

    @Test
    @DisplayName("외부 서비스가 본문 없는 성공 상태를 반환해도 결과 오류로 처리한다")
    void rejectsEmptyResponseBody() {
        server.expect(requestTo("https://example.test/v1/responses"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertFailure(FailureType.EXTERNAL_SERVICE_FAILURE, "INVALID_STUDY_EXPLANATION");
    }

    private void assertFailure(FailureType type, String code) {
        assertThatExceptionOfType(StudyExplanationException.class)
                .isThrownBy(() -> generator.generate("학습 자료"))
                .satisfies(ex -> {
                    assertThat(ex.getFailureType()).isEqualTo(type);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
        server.verify();
    }

    private String response(String output) throws Exception {
        return mapper.writeValueAsString(Map.of("status", "completed", "output", List.of(
                Map.of("type", "reasoning"),
                Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", output))))));
    }
}
