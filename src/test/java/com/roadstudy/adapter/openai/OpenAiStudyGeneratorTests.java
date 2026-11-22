package com.roadstudy.adapter.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstudy.application.study.exception.StudyGenerationException;
import com.roadstudy.application.study.model.*;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.StudyPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("OpenAI 학습 생성 어댑터")
class OpenAiStudyGeneratorTests {
    MockRestServiceServer server;
    OpenAiStudyGenerator generator;
    ObjectMapper mapper = new ObjectMapper();
    StudyDocument document = new StudyDocument(List.of(new StudyDocument.Page(1, "Threads and scheduling")));

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        generator = new OpenAiStudyGenerator(builder.build(), mapper, "gpt-5.4-mini", 24000, 3);
    }

    @Test
    void createsGroundedPlanAndRetainsSourcesWhenSearchFails() throws Exception {
        respond("{\"analysis\":\"[page 1] Threads\",\"suspicions\":[\"a claim\"]}");
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        respond("{\"title\":\"제목\",\"summary\":\"요약\",\"chapters\":[{\"title\":\"스레드\",\"objective\":\"실행 흐름 이해\",\"pages\":[1]}],\"notices\":[]}");
        StudyPlan plan = generator.generatePlan(document, new GenerationBudget(200000, 20));
        assertThat(plan.chapters()).hasSize(1);
        assertThat(plan.notices()).anyMatch(notice -> notice.contains("검증"));
        server.verify();
    }

    @Test
    void rejectsInventedSourcePages() throws Exception {
        respond("{\"analysis\":\"[page 1] Threads\",\"suspicions\":[]}");
        respond("{\"title\":\"제목\",\"summary\":\"요약\",\"chapters\":[{\"title\":\"장\",\"objective\":\"목표\",\"pages\":[99]}],\"notices\":[]}");
        assertThatThrownBy(() -> generator.generatePlan(document, new GenerationBudget(200000, 20)))
                .isInstanceOf(StudyGenerationException.class);
    }

    @Test
    void classifiesAuthenticationFailureAsNonRetryable() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> generator.generatePlan(document, new GenerationBudget(200000, 20)))
                .isInstanceOfSatisfying(StudyGenerationException.class, failure -> assertThat(failure.isRetryable()).isFalse());
    }

    @Test
    void classifiesRateLimitAsRetryable() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> generator.generatePlan(document, new GenerationBudget(200000, 20)))
                .isInstanceOfSatisfying(StudyGenerationException.class, failure -> assertThat(failure.isRetryable()).isTrue());
    }

    @Test
    void detailedRequestKeepsTopicAndRejectsBlankScript() throws Exception {
        respond("{\"script\":\" \"}");
        StudyPlan plan = new StudyPlan("제목", "요약",
                List.of(new StudyPlan.Chapter("장", "목표", List.of(1))), List.of());
        assertThatThrownBy(() -> generator.generateExplanation(document, plan, 0, AudioVariant.DETAILED,
                new GenerationBudget(200000, 20))).isInstanceOf(StudyGenerationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{\"script\":null}", "{\"script\":7}", "{\"script\":\"\"}", "{\"script\":\"대본\"} trailing"})
    void rejectsMalformedOrMissingScript(String result) throws Exception {
        respond(result);
        StudyPlan plan = new StudyPlan("제목", "요약",
                List.of(new StudyPlan.Chapter("장", "목표", List.of(1))), List.of());
        assertThatThrownBy(() -> generator.generateExplanation(document, plan, 0, AudioVariant.BASIC,
                new GenerationBudget(200000, 20))).isInstanceOf(StudyGenerationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 408, 429, 500, 502, 503, 504})
    void retainsRetryClassificationForHttpFailures(int status) {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));
        boolean retryable = status == 408 || status == 429 || status >= 500;
        assertThatThrownBy(() -> generator.generatePlan(document, new GenerationBudget(200000, 20)))
                .isInstanceOfSatisfying(StudyGenerationException.class, error -> assertThat(error.isRetryable()).isEqualTo(retryable));
    }

    @Test
    void rejectsIncompleteOutputRatherThanAcceptingTruncatedScript() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("{\"status\":\"incomplete\",\"output\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> generator.generatePlan(document, new GenerationBudget(200000, 20)))
                .isInstanceOf(StudyGenerationException.class);
    }

    @Test
    void longDocumentAnalysisIncludesLastPage() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        generator = new OpenAiStudyGenerator(builder.build(), mapper, "gpt-5.4-mini", 1000, 0);
        for (int i = 0; i < 4; i++) respond("{\"analysis\":\"구간 개념과 마지막 개념\",\"suspicions\":[]}");
        respond("{\"title\":\"제목\",\"summary\":\"요약\",\"chapters\":[{\"title\":\"장\",\"objective\":\"목표\",\"pages\":[1,2]}],\"notices\":[]}");
        StudyDocument longDocument = new StudyDocument(List.of(
                new StudyDocument.Page(1, "a".repeat(2000)), new StudyDocument.Page(2, "b".repeat(1990) + "LAST_PAGE!")));
        StudyPlan plan = generator.generatePlan(longDocument, new GenerationBudget(200000, 20));
        assertThat(plan.chapters().getFirst().pages()).containsExactly(1, 2);
        server.verify();
    }

    private void respond(String json) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("status", "completed", "output", List.of(
                java.util.Map.of("type", "message", "content", List.of(java.util.Map.of("type", "output_text", "text", json))))));
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
