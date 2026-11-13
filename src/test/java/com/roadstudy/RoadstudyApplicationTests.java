package com.roadstudy;

import java.time.Duration;
import java.time.LocalDateTime;

import com.jayway.jsonpath.JsonPath;
import com.roadstudy.application.study.ExtractedStudyText;
import com.roadstudy.application.study.StudyExplanation;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudySessionCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:roadstudy;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "OPENAI_API_KEY=test-key",
        "AUDIO_STORAGE_PATH=build/tmp/application-test-audio"
})
@AutoConfigureMockMvc
@DisplayName("애플리케이션 통합 테스트")
class RoadstudyApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired StudyRepository studyRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    // 문서 읽기와 유료 외부 API만 대체하고 스레드 풀, 서비스, DB, 음성 저장소는 실제 빈을 사용한다.
    @MockitoBean DocumentReader documentReader;
    @MockitoBean StudyGenerator studyGenerator;
    @MockitoBean SpeechSynthesizer speechSynthesizer;

    @Test
    @DisplayName("Swagger UI와 OpenAPI 문서를 제공한다")
    void exposesSwagger() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("RoadStudy API"))
                .andExpect(jsonPath("$.paths['/api/studies/extract-text'].post.summary").value("PDF 텍스트 추출"))
                .andExpect(jsonPath("$.paths['/api/studies/extract-text'].post.requestBody"
                        + ".content['multipart/form-data'].schema.properties.file.format").value("binary"))
                .andExpect(jsonPath("$.paths['/api/studies/extract-text'].post.responses['422']"
                        + ".content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/studies/generate-audio'].post.summary").value("학습 음성 생성 (임시)"))
                .andExpect(jsonPath("$.paths['/api/studies/generate-audio'].post.requestBody"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/GenerateAudioRequest"))
                .andExpect(jsonPath("$.paths['/api/studies/audio/{audioId}'].get.responses['200']"
                        + ".content['audio/mpeg'].schema.format").value("binary"))
                .andExpect(jsonPath("$.paths['/api/studies/audio/{audioId}'].get.responses['404']"
                        + ".content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/studies/audio/{audioId}'].get.responses['206']"
                        + ".content['audio/mpeg'].schema.format").value("binary"))
                .andExpect(jsonPath("$.paths['/api/studies/audio/{audioId}'].get.responses['416']"
                        + ".content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/studies'].get.responses['200']"
                        + ".content['application/json'].schema.items['$ref']")
                        .value("#/components/schemas/StudyListItemResponse"))
                .andExpect(jsonPath("$.paths['/api/studies'].post.summary").value("학습 자동 생성 시작"))
                .andExpect(jsonPath("$.paths['/api/studies'].post.requestBody"
                        + ".content['multipart/form-data'].schema.properties.file.format").value("binary"))
                .andExpect(jsonPath("$.paths['/api/studies'].post.responses['202']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/StudyResponse"))
                .andExpect(jsonPath("$.paths['/api/studies/{studyId}'].get.summary").value("학습 세션 조회"))
                .andExpect(jsonPath("$.paths['/api/studies/{studyId}'].get.responses['404']"
                        + ".content['application/problem+json']").exists());
    }

    @Test
    @Transactional
    @DisplayName("학습 목록은 빈 배열을 지원하고 생성 시각과 ID 내림차순으로 요약 정보만 반환한다")
    void listsStudySummaries() throws Exception {
        jdbcTemplate.update("delete from study_sessions");
        mvc.perform(get("/api/studies"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        LocalDateTime sameTime = LocalDateTime.of(2026, 1, 2, 12, 0);
        var first = saveStudy("first.pdf", sameTime);
        first.changeExplanation("학습 제목", "학습 요약", "설명 대본");
        studyRepository.save(first);
        var second = saveStudy("second.pdf", sameTime);
        var older = saveStudy("older.pdf", sameTime.minusDays(1));

        mvc.perform(get("/api/studies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(second.getId().intValue()))
                .andExpect(jsonPath("$[1].id").value(first.getId().intValue()))
                .andExpect(jsonPath("$[2].id").value(older.getId().intValue()))
                .andExpect(jsonPath("$[0].originalFileName").value("second.pdf"))
                .andExpect(jsonPath("$[0].title").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].summary").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].summary").value("학습 요약"))
                .andExpect(jsonPath("$[1].explanationScript").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].explanationScript").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[0].audioUrl").doesNotHaveJsonPath());
    }

    private StudySession saveStudy(String fileName, LocalDateTime createdAt) {
        var session = StudySession.create(new StudySessionCreateRequest(fileName));
        ReflectionTestUtils.setField(session, "createdAt", createdAt);
        return studyRepository.save(session);
    }

    @Test
    @DisplayName("실제 빈 구성에서 없는 학습 세션 조회는 404 ProblemDetail을 반환한다")
    void returnsNotFoundForUnknownStudy() throws Exception {
        mvc.perform(get("/api/studies/{studyId}", 404))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDY_NOT_FOUND"));
    }

    @Test
    @DisplayName("실제 스레드 풀과 저장소로 업로드한 학습을 백그라운드에서 완료하고 음성을 제공한다")
    void completesUploadedStudyInBackground() throws Exception {
        byte[] audio = {(byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4};
        when(documentReader.read(any())).thenReturn(new ExtractedStudyText(1, "학습 자료"));
        when(studyGenerator.generate("학습 자료")).thenReturn(new StudyExplanation("제목", "요약", "설명 대본"));
        when(speechSynthesizer.synthesize("설명 대본")).thenReturn(audio);

        String location = mvc.perform(multipart("/api/studies")
                        .file(new MockMultipartFile("file", "lecture.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1})))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get(location))
                .andExpect(jsonPath("$.status").value("COMPLETED")));
        String body = mvc.perform(get(location))
                .andExpect(jsonPath("$.explanationScript").value("설명 대본"))
                .andReturn().getResponse().getContentAsString();
        String audioUrl = JsonPath.read(body, "$.audioUrl");

        mvc.perform(get(audioUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(audio));
    }
}
