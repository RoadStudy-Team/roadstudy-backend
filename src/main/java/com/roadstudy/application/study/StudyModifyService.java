package com.roadstudy.application.study;

import java.io.IOException;
import java.io.InputStream;

import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.port.in.ExtractStudyText;
import com.roadstudy.application.study.port.out.DocumentReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyModifyService implements ExtractStudyText {
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final DocumentReader documentReader;

    @Override
    public ExtractedStudyText extract(InputStream input) throws IOException {
        byte[] content = input.readNBytes(MAX_FILE_BYTES + 1);

        if (content.length == 0) {
            throw PdfExtractionException.emptyFile();
        }

        if (content.length > MAX_FILE_BYTES) {
            throw PdfExtractionException.fileTooLarge();
        }

        var document = documentReader.read(content);
        String text = document.text().strip();
        if (text.isBlank()) {
            throw PdfExtractionException.emptyText();
        }

        return new ExtractedStudyText(document.pageCount(), text);
    }
}
