package com.roadstudy.adapter.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstudy.adapter.pdf.PdfBoxDocumentReader;
import com.roadstudy.adapter.storage.LocalAudioStorage;
import com.roadstudy.application.study.InMemoryStudyRepository;
import com.roadstudy.application.study.StudyExplanation;
import com.roadstudy.application.study.StudyModifyService;
import com.roadstudy.application.study.StudyQueryService;
import com.roadstudy.application.study.exception.StudyExplanationException;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.global.exception.GlobalExceptionHandler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("학습 자동 생성·조회 API")
class StudyGenerationApiTests {
    private static final String ENDPOINT = "/api/studies";
    private static final String STUDY_ENDPOINT = "/api/studies/{studyId}";
    private static final byte[] AUDIO_BYTES = {(byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4};

    private final ObjectMapper mapper = new ObjectMapper();
    private final StudyGenerator generator = mock(StudyGenerator.class);
    private final SpeechSynthesizer synthesizer = mock(SpeechSynthesizer.class);
    private final InMemoryStudyRepository repository = new InMemoryStudyRepository();
    // 넘겨받은 작업을 바로 실행하지 않고 모아 두어, 접수 응답과 백그라운드 완료 뒤의 조회를 나눠 검증한다.
    private final Deque<Runnable> backgroundTasks = new ArrayDeque<>();

    @TempDir Path directory;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var storage = new LocalAudioStorage(directory.toString());
        var modifyService = new StudyModifyService(
                new PdfBoxDocumentReader(), generator, repository, synthesizer, storage, backgroundTasks::add);
        var queryService = new StudyQueryService(storage, repository);
        mvc = MockMvcBuilders.standaloneSetup(new StudyController(
                        modifyService, modifyService, modifyService, queryService, modifyService, queryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("PDF를 올리면 생성이 끝나기 전에 처리 중인 세션과 조회 주소를 202로 반환한다")
    void acceptsUploadBeforeGenerationFinishes() throws Exception {
        upload(pdf("Hello PDF"))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/studies/1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.originalFileName").value("lecture.pdf"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.explanationScript").value(nullValue()))
                .andExpect(jsonPath("$.audioUrl").value(nullValue()));

        assertThat(backgroundTasks).hasSize(1);
        verifyNoInteractions(generator, synthesizer);
    }

    @Test
    @DisplayName("백그라운드 생성이 끝나면 세션 조회가 완료 상태와 대본, 재생 가능한 음성 주소를 반환한다")
    void returnsCompletedStudyWithPlayableAudio() throws Exception {
        when(generator.generate("Hello PDF")).thenReturn(new StudyExplanation("제목", "요약", "생성된 대본"));
        when(synthesizer.synthesize("생성된 대본")).thenReturn(AUDIO_BYTES);
        String location = upload(pdf("Hello PDF")).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        backgroundTasks.remove().run();

        String body = mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.summary").value("요약"))
                .andExpect(jsonPath("$.explanationScript").value("생성된 대본"))
                .andReturn().getResponse().getContentAsString();
        String audioUrl = mapper.readTree(body).path("audioUrl").asText();

        mvc.perform(get(audioUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(AUDIO_BYTES));
    }

    @Test
    @DisplayName("백그라운드 생성이 실패하면 세션 조회가 실패 상태를 반환한다")
    void returnsFailedStudy() throws Exception {
        when(generator.generate("Hello PDF")).thenThrow(StudyExplanationException.generationTimedOut());
        String location = upload(pdf("Hello PDF")).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        backgroundTasks.remove().run();

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.audioUrl").value(nullValue()));
        verifyNoInteractions(synthesizer);
    }

    @Test
    @DisplayName("PDF 확장자로 위장한 파일은 세션을 만들지 않고 400으로 즉시 거부한다")
    void rejectsDisguisedFileImmediately() throws Exception {
        expectProblem(upload(new byte[]{1}), 400, "INVALID_PDF");

        assertThat(repository.savedStatuses()).isEmpty();
        assertThat(backgroundTasks).isEmpty();
    }

    @Test
    @DisplayName("PDF 텍스트가 비어 있으면 세션을 만들지 않고 422로 즉시 거부한다")
    void rejectsBlankPdfImmediately() throws Exception {
        expectProblem(upload(pdf("   ")), 422, "PDF_TEXT_EMPTY");

        assertThat(repository.savedStatuses()).isEmpty();
        assertThat(backgroundTasks).isEmpty();
    }

    @Test
    @DisplayName("파일 파트가 없으면 400을 반환한다")
    void rejectsUploadWithoutFile() throws Exception {
        mvc.perform(multipart(ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(backgroundTasks).isEmpty();
    }

    @Test
    @DisplayName("없는 학습 세션을 조회하면 404를 반환한다")
    void rejectsUnknownStudy() throws Exception {
        expectProblem(mvc.perform(get(STUDY_ENDPOINT, 404)), 404, "STUDY_NOT_FOUND");
    }

    @Test
    @DisplayName("숫자가 아닌 학습 세션 ID는 400을 반환한다")
    void rejectsMalformedStudyId() throws Exception {
        expectProblem(mvc.perform(get(STUDY_ENDPOINT, "not-a-number")), 400, "HTTP_400");
    }

    private ResultActions upload(byte[] content) throws Exception {
        return mvc.perform(multipart(ENDPOINT)
                .file(new MockMultipartFile("file", "lecture.pdf", MediaType.APPLICATION_PDF_VALUE, content)));
    }

    private static ResultActions expectProblem(ResultActions result, int expectedStatus, String expectedCode) throws Exception {
        return result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private static byte[] pdf(String text) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
