package com.roadstudy.adapter.pdf;

import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.model.StudyDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PDFBox 문서 추출 어댑터")
class PdfBoxDocumentReaderTests {
    private final PdfBoxDocumentReader reader = new PdfBoxDocumentReader(500, 500000);

    @Test
    void extractsTextAndPreservesBlankPageNumbers() throws Exception {
        StudyDocument document = reader.read(pdf("Threads", "", "Scheduling"));
        assertThat(document.pages()).hasSize(3);
        assertThat(document.pages().get(1).text()).isBlank();
        assertThat(document.pages().get(2).number()).isEqualTo(3);
        assertThat(document.text()).contains("Threads", "Scheduling");
    }

    @Test
    void rejectsInvalidPdf() {
        assertThatThrownBy(() -> reader.read(new byte[]{1, 2, 3}))
                .isInstanceOfSatisfying(PdfExtractionException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_PDF"));
        assertThatThrownBy(() -> reader.read("%PDF-broken".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .isInstanceOfSatisfying(PdfExtractionException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_PDF"));
    }

    @Test
    void rejectsBlankPdfWithoutInventingContent() throws Exception {
        byte[] content = pdf("");
        assertThatThrownBy(() -> reader.read(content)).isInstanceOfSatisfying(PdfExtractionException.class,
                error -> assertThat(error.getCode()).isEqualTo("PDF_TEXT_EMPTY"));
    }

    @Test
    void enforcesPageAndTextLimitsWithoutTruncating() throws Exception {
        byte[] content = pdf("Threads", "", "Scheduling");
        assertThatThrownBy(() -> new PdfBoxDocumentReader(2, 10000).read(content))
                .isInstanceOfSatisfying(PdfExtractionException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STUDY_SOURCE_TOO_LONG"));
        assertThatThrownBy(() -> new PdfBoxDocumentReader(10, 2).read(content))
                .isInstanceOfSatisfying(PdfExtractionException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STUDY_SOURCE_TOO_LONG"));
    }

    @Test
    void rejectsPasswordProtectedPdfWithSafeExtractionError() throws Exception {
        byte[] content;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);
            content = output.toByteArray();
        }
        assertThatThrownBy(() -> reader.read(content)).isInstanceOfSatisfying(PdfExtractionException.class,
                error -> assertThat(error.getCode()).isEqualTo("PDF_EXTRACTION_FAILED"));
    }

    private byte[] pdf(String... texts) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : texts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
