package com.roadstudy;

import com.fasterxml.jackson.databind.*;
import com.roadstudy.application.study.exception.StudyGenerationException;
import com.roadstudy.application.study.model.*;
import com.roadstudy.application.study.port.out.*;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.StudyPlan;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:sessions;MODE=PostgreSQL",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "STUDY_SOURCE_PATH=build/tmp/session-sources", "AUDIO_STORAGE_PATH=build/tmp/session-audio",
        "STUDY_GENERATION_RETRY_DELAY_MS=0", "OPENAI_API_KEY=test-key"})
@AutoConfigureMockMvc
@DisplayName("챕터 학습 API 통합")
class StudySessionApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StudyGenerator generator;
    @MockitoBean SpeechSynthesizer tts;
    static final String ROOT = "/api/studies";

    @BeforeEach
    void setup() {
        jdbc.update("delete from study_audios");
        jdbc.update("delete from study_chapters");
        jdbc.update("delete from study_sessions");
        reset(generator, tts);
        when(generator.generatePlan(any(), any())).thenReturn(new StudyPlan("학습", "요약", List.of(
                new StudyPlan.Chapter("Threads", "실행 흐름", List.of(1)),
                new StudyPlan.Chapter("Scheduling", "스케줄링", List.of(2))), List.of()));
        when(generator.generateExplanation(any(), any(), anyInt(), eq(AudioVariant.BASIC), any())).thenReturn("기본 초안");
        when(generator.generateExplanation(any(), any(), anyInt(), eq(AudioVariant.DETAILED), any())).thenReturn("상세 초안");
        when(generator.reviewExplanation(any(), any(), anyInt(), anyString(), any())).thenAnswer(call -> call.getArgument(3) + " 검토");
        when(tts.synthesize(anyString())).thenReturn(new byte[]{1, 2, 3, 4, 5, 6});
    }

    @Test
    void uploadsRealPdfAndGeneratesOrderedChaptersWithRangeAudioAndReusableDetail() throws Exception {
        String location = upload();
        JsonNode session = waitFor(location, "COMPLETED");
        assertThat(session.path("chapters")).hasSize(2);
        JsonNode chapter = session.path("chapters").get(0);
        String basicUrl = chapter.path("audios").get(0).path("audioUrl").asText().replace("/api/studies", ROOT);
        mvc.perform(get(basicUrl)).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3, 4, 5, 6}));
        mvc.perform(get(basicUrl).header("Range", "bytes=1-3")).andExpect(status().isPartialContent())
                .andExpect(content().bytes(new byte[]{2, 3, 4}));
        mvc.perform(get(basicUrl).header("Range", "bytes=100-200")).andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */6"));
        for (String invalidRange : List.of("bytes=a-b", "items=0-1", "bytes=-0", "bytes=9-1")) {
            mvc.perform(get(basicUrl).header("Range", invalidRange))
                    .andExpect(status().isRequestedRangeNotSatisfiable())
                    .andExpect(header().string("Content-Range", "bytes */6"));
        }
        long chapterId = chapter.path("id").asLong();
        mvc.perform(post(location + "/chapters/" + chapterId + "/details")).andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode refreshed = read(location);
            assertThat(refreshed.path("chapters").get(0).path("audios")).hasSize(2);
            assertThat(refreshed.path("chapters").get(0).path("audios").findValuesAsText("status"))
                    .containsOnly("READY");
        });
        mvc.perform(post(location + "/chapters/" + chapterId + "/details")).andExpect(status().isAccepted());
        verify(generator, times(1)).generateExplanation(any(), any(), eq(0), eq(AudioVariant.DETAILED), any());
        mvc.perform(get(basicUrl)).andExpect(content().bytes(new byte[]{1, 2, 3, 4, 5, 6}));
    }

    @Test
    void partialFailureKeepsOtherChaptersAndRetriesOnlyMissingAudio() throws Exception {
        when(tts.synthesize(anyString())).thenThrow(com.roadstudy.application.study.exception.StudyAudioException.generationTimedOut())
                .thenThrow(com.roadstudy.application.study.exception.StudyAudioException.generationTimedOut())
                .thenThrow(com.roadstudy.application.study.exception.StudyAudioException.generationTimedOut()).thenReturn(new byte[]{1, 2});
        String location = upload();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode session = read(location);
            assertThat(session.path("status").asText()).isEqualTo("PARTIALLY_READY");
            assertThat(session.path("chapters").get(0).path("audios").get(0).path("status").asText()).isEqualTo("FAILED");
            assertThat(session.path("chapters").get(1).path("audios").get(0).path("status").asText()).isEqualTo("READY");
            assertThat(session.path("generationActive").asBoolean()).isFalse();
        });
        mvc.perform(post(location + "/retry")).andExpect(status().isAccepted());
        waitFor(location, "COMPLETED");
        verify(generator, times(2)).generateExplanation(any(), any(), anyInt(), eq(AudioVariant.BASIC), any());
        verify(generator, times(1)).generatePlan(any(), any());
    }

    @Test
    void rejectsMissingFileAndUnusablePdfAndUnknownIds() throws Exception {
        mvc.perform(multipart(ROOT)).andExpect(status().isBadRequest());
        mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "bad.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/999")).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/audio/999")).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/audio/not-a-number")).andExpect(status().isBadRequest());
        verifyNoInteractions(generator, tts);
    }

    @Test
    void exposesChapterContractInSwaggerWithoutObsoleteMvpEndpoints() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/studies'].post.summary").value("챕터형 학습 생성"))
                .andExpect(jsonPath("$.paths['/api/studies/{sessionId}/chapters/{chapterId}/details'].post").exists())
                .andExpect(jsonPath("$.paths['/api/studies/generate-audio']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.StudySessionResponse.properties.generationActive").exists());
    }

    private String upload() throws Exception {
        return mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "lecture.pdf", "application/pdf", pdf())))
                .andExpect(status().isAccepted()).andReturn().getResponse().getHeader("Location").replace("/api/studies", ROOT);
    }

    private JsonNode read(String location) throws Exception {
        return mapper.readTree(mvc.perform(get(location)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode waitFor(String location, String status) throws Exception {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(read(location).path("status").asText()).isEqualTo(status));
        return read(location);
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : List.of("Threads", "Scheduling")) {
                PDPage page = new PDPage(); document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700); stream.showText(text); stream.endText();
                }
            }
            document.save(output); return output.toByteArray();
        }
    }
}
