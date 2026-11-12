package com.roadstudy.application.study;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.exception.StudyExplanationException;
import com.roadstudy.application.study.exception.StudyNotFoundException;
import com.roadstudy.application.study.port.in.ExtractStudyText;
import com.roadstudy.application.study.port.in.GenerateStudyAudio;
import com.roadstudy.application.study.port.in.GenerateStudyExplanation;
import com.roadstudy.application.study.port.in.StartStudyGeneration;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudySessionCreateRequest;
import com.roadstudy.global.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyModifyService
        implements ExtractStudyText, GenerateStudyExplanation, GenerateStudyAudio, StartStudyGeneration {
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024;
    private static final int MAX_GENERATION_CHARACTERS = 60_000;
    // Google TTS 입력 제한은 UTF-8 바이트 기준이다.
    private static final int MAX_SCRIPT_BYTES = 5_000;

    private final DocumentReader documentReader;
    private final StudyGenerator studyGenerator;
    private final StudyRepository studyRepository;
    private final SpeechSynthesizer speechSynthesizer;
    private final AudioStorage audioStorage;
    private final Executor studyGenerationExecutor;

    @Override
    public StudySession generate(String originalFileName, InputStream input) throws IOException {
        String text = extractGenerationText(input);

        StudySession session = StudySession.create(new StudySessionCreateRequest(originalFileName));
        StudyExplanation explanation = studyGenerator.generate(text);
        session.changeExplanation(explanation.title(), explanation.summary(), explanation.explanationScript());
        return studyRepository.save(session);
    }

    @Override
    public StudyAudio generate(String explanationScript) {
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
    public StudySession start(String originalFileName, InputStream input) throws IOException {
        // 업로드 스트림은 요청이 끝나면 닫히므로 텍스트 추출과 입력 검증은 요청 안에서 끝낸다.
        String text = extractGenerationText(input);

        StudySession session = studyRepository.save(
                StudySession.create(new StudySessionCreateRequest(originalFileName)));
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

    private String extractGenerationText(InputStream input) throws IOException {
        String text = extract(input).text();
        if (text.codePointCount(0, text.length()) > MAX_GENERATION_CHARACTERS) {
            throw StudyExplanationException.textTooLong(MAX_GENERATION_CHARACTERS);
        }
        return text;
    }

    private void generateInBackground(Long studyId, String text) {
        try {
            StudySession session = studyRepository.findById(studyId).orElseThrow(StudyNotFoundException::new);
            StudyExplanation explanation = studyGenerator.generate(text);
            session.changeExplanation(explanation.title(), explanation.summary(), explanation.explanationScript());
            session = studyRepository.save(session);

            StudyAudio audio = generate(explanation.explanationScript());
            session.complete(audio.path().toString());
            studyRepository.save(session);
        } catch (RuntimeException ex) {
            // 응답을 이미 보낸 뒤라 예외를 전달할 곳이 없으므로 세션 상태로 실패를 남긴다.
            markFailed(studyId, ex);
        }
    }

    private void markFailed(Long studyId, RuntimeException cause) {
        String code = cause instanceof ApplicationException failure ? failure.getCode() : "INTERNAL_ERROR";
        log.warn("학습 자동 생성에 실패했습니다. 세션 ID {}, 오류 코드 {}, 예외 유형 {}",
                studyId, code, cause.getClass().getSimpleName());
        try {
            // 저장에 실패한 변경이 섞이지 않도록 저장된 상태를 다시 읽어 실패 처리한다.
            StudySession session = studyRepository.findById(studyId).orElseThrow(StudyNotFoundException::new);
            session.fail();
            studyRepository.save(session);
        } catch (RuntimeException ex) {
            log.error("실패한 학습 세션의 상태를 저장하지 못했습니다. 세션 ID {}, 예외 유형 {}",
                    studyId, ex.getClass().getSimpleName());
        }
    }
}
