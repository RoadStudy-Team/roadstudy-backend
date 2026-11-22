package com.roadstudy.adapter.pdf;

import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.model.StudyDocument;
import com.roadstudy.application.study.port.out.DocumentReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class PdfBoxDocumentReader implements DocumentReader {
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private final int maxPages;
    private final int maxCharacters;

    public PdfBoxDocumentReader(@Value("${STUDY_MAX_PAGES:500}") int maxPages,
                                @Value("${STUDY_MAX_EXTRACTED_CHARACTERS:500000}") int maxCharacters) {
        if (maxPages <= 0 || maxCharacters <= 0) {
            throw new IllegalArgumentException("PDF의 최대 페이지 수 또는 추출 문자 수 설정이 올바르지 않습니다.");
        }
        this.maxPages = maxPages;
        this.maxCharacters = maxCharacters;
    }

    @Override
    public StudyDocument read(byte[] content) {
        try (PDDocument document = loadPdf(content)) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw PdfExtractionException.extractionFailed(null);
            }

            if (document.getNumberOfPages() > maxPages) {
                throw PdfExtractionException.documentTooLong();
            }

            return extractPages(document);
        } catch (IOException ex) {
            throw PdfExtractionException.extractionFailed(ex);
        }
    }

    private StudyDocument extractPages(PDDocument document) {
        List<StudyDocument.Page> pages = new ArrayList<>();
        long characters = 0;
        PDFTextStripper stripper = new PDFTextStripper();

        for (int number = 1; number <= document.getNumberOfPages(); number++) {
            String text = extractPage(document, stripper, number);
            characters += text.codePointCount(0, text.length());

            if (characters > maxCharacters) {
                throw PdfExtractionException.documentTooLong();
            }

            pages.add(new StudyDocument.Page(number, text));
        }

        if (pages.isEmpty() || pages.stream().allMatch(page -> page.text().isBlank())) {
            throw PdfExtractionException.emptyText();
        }

        return new StudyDocument(pages);
    }

    private String extractPage(PDDocument document, PDFTextStripper stripper, int number) {
        stripper.setStartPage(number);
        stripper.setEndPage(number);

        try {
            return stripper.getText(document).strip();
        } catch (IOException ex) {
            // 읽지 못한 페이지도 번호 유지, 나머지 페이지 추출 계속
            return "";
        }
    }

    private PDDocument loadPdf(byte[] content) {
        if (content == null || content.length < PDF_HEADER.length
                || !Arrays.equals(content, 0, PDF_HEADER.length, PDF_HEADER, 0, PDF_HEADER.length)) {
            throw PdfExtractionException.invalidPdf(null);
        }
        try {
            return Loader.loadPDF(content);
        } catch (InvalidPasswordException ex) {
            throw PdfExtractionException.extractionFailed(ex);
        } catch (IOException ex) {
            throw PdfExtractionException.invalidPdf(ex);
        }
    }
}
