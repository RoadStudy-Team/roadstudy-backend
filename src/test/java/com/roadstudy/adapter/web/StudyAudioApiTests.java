package com.roadstudy.adapter.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstudy.adapter.storage.LocalAudioStorage;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.application.study.StudyModifyService;
import com.roadstudy.application.study.StudyQueryService;
import com.roadstudy.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("학습 음성 생성·조회 API")
class StudyAudioApiTests {
    private static final String GENERATE_ENDPOINT = "/api/studies/generate-audio";
    private static final String AUDIO_ENDPOINT = "/api/studies/audio/{audioId}";
    private static final String SCRIPT = "오늘은 자동차의 첨단 운전자 보조 시스템에 대해 알아보겠습니다.";
    private static final byte[] AUDIO_BYTES = {(byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4, 0x00, 0x00};

    private final ObjectMapper mapper = new ObjectMapper();
    private final SpeechSynthesizer synthesizer = mock(SpeechSynthesizer.class);

    @TempDir Path directory;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = mvc(new LocalAudioStorage(directory.toString()));
    }

    @Test
    @DisplayName("대본으로 저장한 음성 바이트를 반환 주소에서 조회한다")
    void generatesAndServesAudio() throws Exception {
        when(synthesizer.synthesize(SCRIPT)).thenReturn(AUDIO_BYTES);

        String body = generate(SCRIPT)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        String audioId = mapper.readTree(body).path("audioId").asText();
        String audioUrl = mapper.readTree(body).path("audioUrl").asText();

        assertThat(UUID.fromString(audioId)).hasToString(audioId);
        assertThat(audioUrl).isEqualTo("/api/studies/audio/" + audioId);
        assertThat(Files.readAllBytes(directory.resolve(audioId + ".mp3"))).isEqualTo(AUDIO_BYTES);

        mvc.perform(get(audioUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, AUDIO_BYTES.length))
                .andExpect(content().bytes(AUDIO_BYTES));
    }

    @Test
    @DisplayName("플레이어가 구간을 요청하면 해당 부분만 206으로 반환한다")
    void servesByteRange() throws Exception {
        when(synthesizer.synthesize(SCRIPT)).thenReturn(AUDIO_BYTES);
        String body = generate(SCRIPT).andReturn().getResponse().getContentAsString();

        mvc.perform(get(mapper.readTree(body).path("audioUrl").asText()).header(HttpHeaders.RANGE, "bytes=2-3"))
                .andExpect(status().isPartialContent())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-3/" + AUDIO_BYTES.length))
                .andExpect(content().bytes(new byte[]{0x44, (byte) 0xC4}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"explanationScript\":null}", "{\"explanationScript\":\"\"}", "{\"explanationScript\":\"  \"}"})
    @DisplayName("대본이 없거나 비어 있으면 400을 반환하고 합성기를 호출하지 않는다")
    void rejectsEmptyScript(String body) throws Exception {
        expectProblem(mvc.perform(post(GENERATE_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body)),
                400, "EMPTY_SCRIPT");
        verifyNoInteractions(synthesizer);
    }

    @Test
    @DisplayName("대본이 5,000바이트를 넘으면 422를 반환하고 합성기를 호출하지 않는다")
    void rejectsTooLongScript() throws Exception {
        expectProblem(generate("가".repeat(1_667)), 422, "TTS_INPUT_TOO_LONG");
        verifyNoInteractions(synthesizer);
    }

    @Test
    @DisplayName("음성 합성 서비스 호출 실패를 502 ProblemDetail로 반환한다")
    void returnsGenerationFailure() throws Exception {
        var cause = new IllegalStateException("외부에 공개할 수 없는 인증 오류");
        when(synthesizer.synthesize(SCRIPT)).thenThrow(StudyAudioException.generationFailed(cause));

        var response = expectProblem(generate(SCRIPT), 502, "TTS_GENERATION_FAILED")
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("인증 오류");
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    @DisplayName("음성 합성 타임아웃을 504 ProblemDetail로 반환한다")
    void returnsGenerationTimeout() throws Exception {
        when(synthesizer.synthesize(SCRIPT)).thenThrow(StudyAudioException.generationTimedOut());

        expectProblem(generate(SCRIPT), 504, "TTS_TIMEOUT");
    }

    @Test
    @DisplayName("음성 파일 저장에 실패하면 500을 반환하고 내부 경로를 노출하지 않는다")
    void returnsStorageFailure() throws Exception {
        when(synthesizer.synthesize(SCRIPT)).thenReturn(AUDIO_BYTES);
        Path file = Files.createFile(directory.resolve("not-a-directory"));
        mvc = mvc(new LocalAudioStorage(file.resolve("audio").toString()));

        expectProblem(generate(SCRIPT), 500, "AUDIO_STORAGE_FAILED")
                .andExpect(jsonPath("$.detail").value("생성한 음성 파일을 저장하지 못했습니다."));
    }

    @Test
    @DisplayName("JSON 본문이 깨져 있으면 400을 반환한다")
    void rejectsMalformedBody() throws Exception {
        expectProblem(mvc.perform(post(GENERATE_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{")),
                400, "HTTP_400");
    }

    @Test
    @DisplayName("생성 요청 본문이 없으면 400 ProblemDetail을 반환한다")
    void rejectsMissingBody() throws Exception {
        expectProblem(mvc.perform(post(GENERATE_ENDPOINT).contentType(MediaType.APPLICATION_JSON)),
                400, "HTTP_400");
        verifyNoInteractions(synthesizer);
    }

    @Test
    @DisplayName("JSON 요청이 아니면 415를 반환한다")
    void rejectsUnsupportedContentType() throws Exception {
        expectProblem(mvc.perform(post(GENERATE_ENDPOINT).contentType(MediaType.TEXT_PLAIN).content(SCRIPT)),
                415, "HTTP_415");
    }

    @ParameterizedTest
    @ValueSource(strings = {"550e8400-e29b-41d4-a716-446655440000", "not-a-uuid", "550e8400-e29b-41d4-a716-446655440000.mp3"})
    @DisplayName("저장되지 않았거나 형식이 잘못된 audioId를 조회하면 404를 반환한다")
    void returnsNotFoundForUnknownAudio(String audioId) throws Exception {
        expectProblem(mvc.perform(get(AUDIO_ENDPOINT, audioId)), 404, "AUDIO_NOT_FOUND");
    }

    @Test
    @DisplayName("플레이어가 음성 형식만 받겠다고 요청해도 404를 ProblemDetail로 반환한다")
    void returnsProblemDetailToAudioPlayers() throws Exception {
        expectProblem(mvc.perform(get(AUDIO_ENDPOINT, UUID.randomUUID()).accept("audio/mpeg")), 404, "AUDIO_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes=100-200", "bytes=invalid"})
    @DisplayName("잘못된 음성 구간 요청은 전체 크기를 포함한 416 ProblemDetail을 반환한다")
    void rejectsInvalidRange(String range) throws Exception {
        when(synthesizer.synthesize(SCRIPT)).thenReturn(AUDIO_BYTES);
        String body = generate(SCRIPT).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String audioUrl = mapper.readTree(body).path("audioUrl").asText();

        expectProblem(mvc.perform(get(audioUrl).header(HttpHeaders.RANGE, range)
                        .accept("audio/mpeg")), 416, "HTTP_416")
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */" + AUDIO_BYTES.length));
    }

    private MockMvc mvc(AudioStorage storage) {
        var modifyService = new StudyModifyService(
                mock(DocumentReader.class), mock(StudyGenerator.class), mock(StudyRepository.class), synthesizer, storage);
        var controller = new StudyController(modifyService, modifyService, modifyService, new StudyQueryService(storage));
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private ResultActions generate(String script) throws Exception {
        return mvc.perform(post(GENERATE_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("explanationScript", script))));
    }

    private static ResultActions expectProblem(ResultActions result, int expectedStatus, String expectedCode) throws Exception {
        return result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }
}
