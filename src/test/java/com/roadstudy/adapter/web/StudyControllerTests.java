package com.roadstudy.adapter.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.roadstudy.adapter.pdf.PdfBoxDocumentReader;
import com.roadstudy.application.study.StudyModifyService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PDF 텍스트 추출 API")
class StudyControllerTests {
    private static final String ENDPOINT = "/api/studies/extract-text";
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new StudyController(new StudyModifyService(new PdfBoxDocumentReader())))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("PDF 파일을 올리면 파일명, 페이지 수, 문자 수와 텍스트를 반환한다")
    void extractsText() throws Exception {
        mvc.perform(multipart(ENDPOINT).file(pdfFile("lecture.pdf", pdf("Hello PDF"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.fileName").value("lecture.pdf"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.characterCount").value("Hello PDF".length()))
                .andExpect(jsonPath("$.text").value("Hello PDF"));
    }

    @Test
    @DisplayName("여러 페이지의 텍스트를 페이지 순서대로 반환한다")
    void extractsPagesInOrder() throws Exception {
        String expected = "First page\nSecond page";
        mvc.perform(multipart(ENDPOINT).file(pdfFile("lecture.pdf", pdf("First page", "Second page"))))
                .andExpect(status().isOk())
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
    @DisplayName("PDF 형식이 아닌 파일을 업로드하면 400을 반환한다")
    void rejectsNonPdfFile() throws Exception {
        var file = new MockMultipartFile("file", "lecture.txt", MediaType.TEXT_PLAIN_VALUE,
                "hello".getBytes(StandardCharsets.UTF_8));
        expectProblem(file, 400, "INVALID_PDF");
    }

    @Test
    @DisplayName("파일명과 콘텐츠 타입을 PDF로 지정해도 실제 형식이 다르면 거부한다")
    void rejectsDisguisedPdfFile() throws Exception {
        expectProblem(pdfFile("lecture.pdf", "hello".getBytes(StandardCharsets.UTF_8)), 400, "INVALID_PDF");
    }

    @Test
    @DisplayName("PDF 헤더 뒤에 올바른 문서 구조가 없으면 400을 반환한다")
    void rejectsMalformedPdf() throws Exception {
        expectProblem(pdfFile("broken.pdf", "%PDF-1.7\ninvalid".getBytes(StandardCharsets.US_ASCII)),
                400, "INVALID_PDF");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("텍스트가 없거나 공백만 있는 PDF를 업로드하면 422를 반환한다")
    void rejectsPdfWithoutText(String text) throws Exception {
        expectProblem(pdfFile("blank.pdf", pdf(text)), 422, "PDF_TEXT_EMPTY");
    }

    @Test
    @DisplayName("암호가 필요한 PDF의 텍스트를 읽을 수 없으면 422를 반환한다")
    void rejectsPasswordProtectedPdf() throws Exception {
        expectProblem(pdfFile("protected.pdf", pdf(new StandardProtectionPolicy("owner", "reader", new AccessPermission()), "Protected")), 422, "PDF_EXTRACTION_FAILED");
    }

    @Test
    @DisplayName("10MB를 초과한 파일을 업로드하면 413을 반환한다")
    void rejectsOversizedFile() throws Exception {
        expectProblem(pdfFile("large.pdf", new byte[MAX_FILE_BYTES + 1]), 413, "FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("정확히 10MB인 정상 PDF를 허용한다")
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
    @DisplayName("file 파트가 없으면 400을 반환한다")
    void rejectsMissingFilePart() throws Exception {
        mvc.perform(multipart(ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("HTTP_400"));
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
    @DisplayName("파일 읽기 실패 시 500을 반환하고 내부 오류 메시지를 숨긴다")
    void sanitizesUnexpectedReadFailure() throws Exception {
        var file = new MockMultipartFile("file", "broken.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1}) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("외부에 공개할 수 없는 파일 읽기 오류");
            }
        };

        expectProblem(file, 500, "INTERNAL_ERROR")
                .andExpect(jsonPath("$.detail").value("서버에서 예상하지 못한 오류가 발생했습니다."));
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
