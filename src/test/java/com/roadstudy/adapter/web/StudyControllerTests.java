package com.roadstudy.adapter.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstudy.adapter.storage.LocalAudioStorage;
import com.roadstudy.application.study.StudyQueryService;

import com.roadstudy.adapter.pdf.PdfBoxDocumentReader;
import com.roadstudy.application.study.exception.StudyExplanationException;
import com.roadstudy.application.study.port.in.FindStudyAudio;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.application.study.StudyExplanation;
import com.roadstudy.application.study.StudyModifyService;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.global.exception.GlobalExceptionHandler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PDF 텍스트 추출 API")
class StudyControllerTests {
    private static final String ENDPOINT = "/api/studies/extract-text";
    private static final String GENERATE_ENDPOINT = "/api/studies/generate-explanation";
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024;

    private final StudyGenerator generator = mock(StudyGenerator.class);
    private final StudyRepository repository = mock(StudyRepository.class);
    private final StudyModifyService service = new StudyModifyService(new PdfBoxDocumentReader(), generator, repository, mock(SpeechSynthesizer.class), mock(AudioStorage.class));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StudyController(service, service, service, mock(FindStudyAudio.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("PDF에서 생성한 대본을 저장하고 세션 ID, 제목, 요약, 대본을 반환한다")
    void generatesExplanation() throws Exception {
        when(generator.generate("Hello PDF")).thenReturn(new StudyExplanation("제목", "요약", "설명"));
        when(repository.save(any(StudySession.class))).thenAnswer(invocation -> {
            StudySession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 42L);
            return session;
        });

        mvc.perform(multipart(GENERATE_ENDPOINT).file(pdfFile("lecture.pdf", pdf("Hello PDF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.summary").value("요약"))
                .andExpect(jsonPath("$.explanationScript").value("설명"));
    }

    @Test
    @DisplayName("대본 생성 요청에 파일 파트가 없으면 400을 반환하고 생성기를 호출하지 않는다")
    void rejectsGenerationWithoutFile() throws Exception {
        mvc.perform(multipart(GENERATE_ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(generator, repository);
    }

    @Test
    @DisplayName("PDF 확장자로 위장한 파일은 대본 생성 전에 400으로 거부한다")
    void rejectsDisguisedGenerationFile() throws Exception {
        mvc.perform(multipart(GENERATE_ENDPOINT).file(pdfFile("lecture.pdf", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PDF"));
        verifyNoInteractions(generator, repository);
    }

    @Test
    @DisplayName("생성 실패는 502로 반환하고 학습 세션을 저장하지 않는다")
    void returnsGenerationFailureWithoutSaving() throws Exception {
        when(generator.generate("Hello PDF")).thenThrow(StudyExplanationException.generationFailed());

        mvc.perform(multipart(GENERATE_ENDPOINT).file(pdfFile("lecture.pdf", pdf("Hello PDF"))))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("STUDY_GENERATION_FAILED"));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("PDF 텍스트가 비어 있으면 생성기를 호출하지 않고 422를 반환한다")
    void rejectsBlankGenerationInput() throws Exception {
        mvc.perform(multipart(GENERATE_ENDPOINT).file(pdfFile("lecture.pdf", pdf("   "))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PDF_TEXT_EMPTY"));
        verifyNoInteractions(generator, repository);
    }

    @Test
    @DisplayName("여러 페이지의 텍스트를 페이지 순서대로 반환한다")
    void extractsPagesInOrder() throws Exception {
        String expected = "First page\nSecond page";
        mvc.perform(multipart(ENDPOINT).file(pdfFile("lecture.pdf", pdf("First page", "Second page"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.fileName").value("lecture.pdf"))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.characterCount").value(expected.length()))
                .andExpect(jsonPath("$.text").value(expected));
    }

    @Test
    @DisplayName("빈 파일을 업로드하면 400을 반환한다")
    void rejectsEmptyFile() throws Exception {
        expectProblem(pdfFile("empty.pdf", new byte[0]), 400, "EMPTY_FILE");
    }

    @Test
    @DisplayName("PDF 헤더 뒤에 올바른 문서 구조가 없으면 400을 반환한다")
    void rejectsMalformedPdf() throws Exception {
        expectProblem(pdfFile("broken.pdf", "%PDF-1.7\ninvalid".getBytes(StandardCharsets.US_ASCII)),
                400, "INVALID_PDF");
    }

    @Test
    @DisplayName("50MB를 1바이트 초과한 파일을 업로드하면 413을 반환한다")
    void rejectsOversizedFile() throws Exception {
        expectProblem(pdfFile("large.pdf", new byte[MAX_FILE_BYTES + 1]), 413, "FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("정확히 50MB인 정상 PDF를 허용한다")
    void acceptsPdfAtSizeLimit() throws Exception {
        byte[] original = pdf("Boundary");
        byte[] padded = new byte[MAX_FILE_BYTES];
        int trailerOffset = new String(original, StandardCharsets.ISO_8859_1).lastIndexOf("startxref");
        int paddingEnd = padded.length - (original.length - trailerOffset);
        // 교차 참조 오프셋을 유지하도록 startxref 앞에 PDF 공백을 채운다.
        System.arraycopy(original, 0, padded, 0, trailerOffset);
        Arrays.fill(padded, trailerOffset, paddingEnd, (byte) ' ');
        padded[paddingEnd - 1] = '\n';
        System.arraycopy(original, trailerOffset, padded, paddingEnd, original.length - trailerOffset);

        mvc.perform(multipart(ENDPOINT).file(pdfFile("limit.pdf", padded)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Boundary"));
    }

    @Test
    @DisplayName("multipart 요청이 아니면 415를 반환한다")
    void rejectsUnsupportedRequestContentType() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("HTTP_415"));
    }

    @Test
    @DisplayName("중간 페이지가 비어 있어도 전체 페이지 수와 텍스트 순서를 유지한다")
    void includesBlankPagesInPageCount() throws Exception {
        mvc.perform(multipart(ENDPOINT).file(pdfFile("lecture.pdf", pdf("First", "", "Last"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageCount").value(3))
                .andExpect(jsonPath("$.text").value(matchesPattern("First\\s+Last")));
    }

    @Test
    @DisplayName("열 수 있어도 텍스트 추출 권한이 없는 PDF는 422를 반환한다")
    void rejectsPdfWithoutExtractionPermission() throws Exception {
        var permission = new AccessPermission();
        permission.setCanExtractContent(false);
        var protection = new StandardProtectionPolicy("owner", "", permission);

        expectProblem(pdfFile("restricted.pdf", pdf(protection, "Restricted")), 422, "PDF_EXTRACTION_FAILED");
    }

    @Test
    @DisplayName("파일 파트의 MIME이 일반 바이너리여도 실제 PDF이면 허용한다")
    void acceptsPdfWithGenericPartContentType() throws Exception {
        var file = new MockMultipartFile("file", "lecture.pdf", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                pdf("Hello PDF"));

        mvc.perform(multipart(ENDPOINT).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Hello PDF"));
    }

    @Test
    @DisplayName("PDF에서 생성한 대본으로 음성을 생성하고 반환된 URL에서 조회한다")
    void generatesAudioFromExplanation(@TempDir Path directory) throws Exception {
        var synthesizer = mock(SpeechSynthesizer.class);
        var storage = new LocalAudioStorage(directory.toString());
        var integratedService = new StudyModifyService(
                new PdfBoxDocumentReader(), generator, repository, synthesizer, storage);
        var integratedMvc = MockMvcBuilders.standaloneSetup(new StudyController(
                        integratedService, integratedService, integratedService, new StudyQueryService(storage)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        var mapper = new ObjectMapper();
        byte[] audioBytes = {(byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4};
        when(generator.generate("Hello PDF")).thenReturn(new StudyExplanation("제목", "요약", "생성된 대본"));
        when(repository.save(any(StudySession.class))).thenAnswer(invocation -> {
            StudySession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 42L);
            return session;
        });
        when(synthesizer.synthesize("생성된 대본")).thenReturn(audioBytes);

        String explanationBody = integratedMvc.perform(multipart(GENERATE_ENDPOINT)
                        .file(pdfFile("lecture.pdf", pdf("Hello PDF"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String script = mapper.readTree(explanationBody).path("explanationScript").asText();
        String audioBody = integratedMvc.perform(post("/api/studies/generate-audio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(java.util.Map.of("explanationScript", script))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String audioUrl = mapper.readTree(audioBody).path("audioUrl").asText();

        integratedMvc.perform(get(audioUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(audioBytes));
    }
    private ResultActions expectProblem(MockMultipartFile file, int expectedStatus, String expectedCode) throws Exception {
        return mvc.perform(multipart(ENDPOINT).file(file))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private static MockMultipartFile pdfFile(String fileName, byte[] content) {
        return new MockMultipartFile("file", fileName, MediaType.APPLICATION_PDF_VALUE, content);
    }

    private static byte[] pdf(String... pages) throws IOException {
        return pdf(null, pages);
    }

    private static byte[] pdf(StandardProtectionPolicy protection, String... pages) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                var page = new PDPage();
                document.addPage(page);
                if (!text.isEmpty()) {
                    try (var stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(font, 12);
                        stream.newLineAtOffset(72, 720);
                        stream.showText(text);
                        stream.endText();
                    }
                }
            }
            if (protection != null) {
                document.protect(protection);
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
