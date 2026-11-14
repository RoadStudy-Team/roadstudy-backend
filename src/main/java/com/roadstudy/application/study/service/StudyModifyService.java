package com.roadstudy.application.study.service;

import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.exception.StudyExplanationException;
import com.roadstudy.application.study.exception.StudyNotFoundException;
import com.roadstudy.application.study.model.ExtractedStudyText;
import com.roadstudy.application.study.model.StudyAudio;
import com.roadstudy.application.study.model.StudyExplanation;
import com.roadstudy.application.study.port.in.GenerateStudy;
import com.roadstudy.application.study.port.in.StudyGenerationSteps;
import com.roadstudy.application.study.port.out.*;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudySessionCreateRequest;
import com.roadstudy.global.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyModifyService implements StudyGenerationSteps, GenerateStudy {
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024;
    private static final int MAX_GENERATION_CHARACTERS = 60_000;
    private static final int MAX_SCRIPT_BYTES = 30_000;

    private final DocumentReader documentReader;
    private final StudyGenerator studyGenerator;
    private final StudyRepository studyRepository;
    private final SpeechSynthesizer speechSynthesizer;
    private final AudioStorage audioStorage;
    private final Executor studyGenerationExecutor;

    @Override
    public StudySession generateExplanation(String originalFileName, InputStream input) {
        String text = extractGenerationText(input);

        StudySession session = StudySession.create(new StudySessionCreateRequest(originalFileName));

        StudyExplanation explanation = studyGenerator.generateExplanation(text);
        session.changeExplanation(explanation.title(), explanation.summary(), explanation.explanationScript());

        return studyRepository.save(session);
    }

    @Override
    public StudyAudio generateAudio(String explanationScript) {
        if (explanationScript == null || explanationScript.isBlank()) {
            throw StudyAudioException.emptyScript();
        }

        String script = explanationScript.strip();
        if (script.getBytes(StandardCharsets.UTF_8).length > MAX_SCRIPT_BYTES) {
            throw StudyAudioException.scriptTooLong(MAX_SCRIPT_BYTES);
        }

        byte[] audio = speechSynthesizer.synthesize(script);
        return audioStorage.save(UUID.randomUUID(), audio);
    }

    @Override
    public StudySession generateStudyAsync(String originalFileName, InputStream input) {
        // 요청이 끝나면 업로드 스트림이 닫히므로, 작업 등록 전에 텍스트 추출
        String text = extractGenerationText(input);

        StudySession session = studyRepository.save(StudySession.create(new StudySessionCreateRequest(originalFileName)));
        Long studyId = session.getId();

        try {
            studyGenerationExecutor.execute(() -> generateInBackground(studyId, text));
        } catch (RuntimeException ex) {
            markFailed(studyId, ex);
            throw ex;
        }
        return session;
    }

    @Override
    public ExtractedStudyText extractText(InputStream input) {
        byte[] content = readPdfContent(input);

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

    private byte[] readPdfContent(InputStream input) {
        try {
            return input.readNBytes(MAX_FILE_BYTES + 1);
        } catch (IOException ex) {
            throw PdfExtractionException.readFailed(ex);
        }
    }

    private String extractGenerationText(InputStream input) {
        String text = extractText(input).text();
        if (text.codePointCount(0, text.length()) > MAX_GENERATION_CHARACTERS) {
            throw StudyExplanationException.textTooLong(MAX_GENERATION_CHARACTERS);
        }
        return text;
    }

    private void generateInBackground(Long studyId, String text) {
        try {
            StudySession session = studyRepository.findById(studyId).orElseThrow(StudyNotFoundException::new);
            StudyExplanation explanation = studyGenerator.generateExplanation(text);
            session.changeExplanation(explanation.title(), explanation.summary(), explanation.explanationScript());

            // 음성 생성이 실패해도 대본은 남도록 먼저 저장
            session = studyRepository.save(session);

            StudyAudio audio = generateAudio(explanation.explanationScript());
            session.complete(audio.path().toString());
            studyRepository.save(session);
        } catch (RuntimeException ex) {
            markFailed(studyId, ex);
        }
    }

    private void markFailed(Long studyId, RuntimeException cause) {
        String code = cause instanceof ApplicationException failure ? failure.getCode() : "INTERNAL_ERROR";
        log.warn("학습 자동 생성에 실패했습니다. 세션 ID {}, 오류 코드 {}, 예외 유형 {}",
                studyId, code, cause.getClass().getSimpleName());
        try {
            // DB에 마지막으로 저장된 세션을 다시 읽어 FAILED로 변경
            StudySession session = studyRepository.findById(studyId).orElseThrow(StudyNotFoundException::new);
            session.fail();
            studyRepository.save(session);
        } catch (RuntimeException ex) {
            log.error("실패한 학습 세션의 상태를 저장하지 못했습니다. 세션 ID {}, 예외 유형 {}",
                    studyId, ex.getClass().getSimpleName());
        }
    }
}
