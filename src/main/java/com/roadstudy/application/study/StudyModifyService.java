package com.roadstudy.application.study;

import java.io.IOException;
import java.io.InputStream;

import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.exception.StudyExplanationException;
import com.roadstudy.application.study.port.in.ExtractStudyText;
import com.roadstudy.application.study.port.in.GenerateStudyExplanation;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudySessionCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyModifyService implements ExtractStudyText, GenerateStudyExplanation {
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024;
    private static final int MAX_GENERATION_CHARACTERS = 60_000;

    private final DocumentReader documentReader;
    private final StudyGenerator studyGenerator;
    private final StudyRepository studyRepository;

    @Override
    public StudySession generate(String originalFileName, InputStream input) throws IOException {
        String text = extract(input).text();
        if (text.codePointCount(0, text.length()) > MAX_GENERATION_CHARACTERS) {
            throw StudyExplanationException.textTooLong(MAX_GENERATION_CHARACTERS);
        }

        StudySession session = StudySession.create(new StudySessionCreateRequest(originalFileName));
        StudyExplanation explanation = studyGenerator.generate(text);
        session.changeExplanation(explanation.title(), explanation.summary(), explanation.explanationScript());
        return studyRepository.save(session);
    }

    @Override
    public ExtractedStudyText extract(InputStream input) throws IOException {
        byte[] content = input.readNBytes(MAX_FILE_BYTES + 1);

        if (content.length == 0) {
            throw PdfExtractionException.emptyFile();
        }

        if (content.length > MAX_FILE_BYTES) {
            throw PdfExtractionException.fileTooLarge();
        }

        ExtractedStudyText document = documentReader.read(content);
        String text = document.text().strip();
        if (text.isBlank()) {
            throw PdfExtractionException.emptyText();
        }

        return new ExtractedStudyText(document.pageCount(), text);
    }
}
