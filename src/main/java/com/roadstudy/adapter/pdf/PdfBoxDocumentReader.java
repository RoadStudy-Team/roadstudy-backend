package com.roadstudy.adapter.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.roadstudy.application.study.ExtractedStudyText;
import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.port.out.DocumentReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxDocumentReader implements DocumentReader {
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Override
    public ExtractedStudyText read(byte[] content) {
        try (var document = load(content)) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw PdfExtractionException.extractionFailed(null);
            }

            var stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            stripper.setPageEnd("\n");
            String text = stripper.getText(document);

            return new ExtractedStudyText(document.getNumberOfPages(), text);
        } catch (IOException ex) {
            throw PdfExtractionException.extractionFailed(ex);
        }
    }

    private PDDocument load(byte[] content) {
        if (content.length < PDF_HEADER.length
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
