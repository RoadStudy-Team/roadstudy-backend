package com.roadstudy.application.study.service;

import com.roadstudy.domain.study.enums.GenerationStage;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.exception.StudyGenerationException;
import com.roadstudy.application.study.exception.StudyNotFoundException;
import com.roadstudy.application.study.model.GenerationBudget;
import com.roadstudy.application.study.model.StudyDocument;
import com.roadstudy.application.study.model.StudyGenerationSettings;
import com.roadstudy.application.study.port.in.GenerateStudy;
import com.roadstudy.application.study.port.out.*;
import com.roadstudy.domain.study.*;
import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyModifyService implements GenerateStudy {
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024;

    private final StudySessionRepository studySessionRepository;
    private final StudyChapterRepository studyChapterRepository;
    private final StudyAudioRepository studyAudioRepository;
    private final StudySourceStorage studySourceStorage;
    private final StudyGenerator studyGenerator;
    private final SpeechSynthesizer speechSynthesizer;
    private final AudioStorage audioStorage;
    private final Executor studyGenerationExecutor;
    private final StudyGenerationSettings studyGenerationSettings;

    @Override
    public StudySession generateStudyAsync(String originalFileName, InputStream input) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw PdfExtractionException.emptyFile();
        }

        byte[] content = readPdfContent(input);
        String sourceKey = saveStudySource(content);
        StudySession session = createStudySession(originalFileName, sourceKey);

        retryGeneration(session.getId());
        return session;
    }

    @Override
    public void retryGeneration(Long sessionId) {
        StudySession session = findStudySession(sessionId);
        if (session.getSourceKey() == null) {
            throw StudyGenerationException.sourceRequired();
        }

        if (!studySessionRepository.claimGeneration(sessionId)) {
            return;
        }

        try {
            studyGenerationExecutor.execute(() -> generateInBackground(sessionId));
        } catch (RuntimeException ex) {
            studySessionRepository.releaseGeneration(sessionId);
            throw ex;
        }
    }

    @Override
    public StudyAudio generateDetailedAudioAsync(Long sessionId, Long chapterId) {
        findStudyChapter(sessionId, chapterId);
        StudyAudio audio = studyAudioRepository.findOrCreate(chapterId, AudioVariant.DETAILED);
        submitAudioGeneration(sessionId, audio);
        return audio;
    }

    @Override
    public void retryAudioGeneration(Long sessionId, Long chapterId, Long audioId) {
        findStudyChapter(sessionId, chapterId);
        StudyAudio audio = findStudyAudio(audioId);
        if (!audio.getChapterId().equals(chapterId)) {
            throw StudyAudioException.notFound();
        }
        submitAudioGeneration(sessionId, audio);
    }

    @Override
    public void interruptGeneration() {
        studySessionRepository.interruptRunning();
        studyAudioRepository.interruptRunning();
    }

    private byte[] readPdfContent(InputStream input) {
        byte[] content;
        try {
            content = input.readNBytes(MAX_FILE_BYTES + 1);
        } catch (IOException ex) {
            throw PdfExtractionException.readFailed(ex);
        }

        if (content.length == 0) {
            throw PdfExtractionException.emptyFile();
        }

        if (content.length > MAX_FILE_BYTES) {
            throw PdfExtractionException.fileTooLarge();
        }

        return content;
    }

    private String saveStudySource(byte[] content) {
        String sourceKey = studySourceStorage.save(UUID.randomUUID(), content);
        try {
            studySourceStorage.read(sourceKey);
            return sourceKey;
        } catch (RuntimeException ex) {
            removeSourceAfterFailure(sourceKey, ex);
            throw ex;
        }
    }

    private StudySession createStudySession(String originalFileName, String sourceKey) {
        StudySession session = StudySession.create(originalFileName);
        session.attachSource(sourceKey);
        try {
            return studySessionRepository.save(session);
        } catch (RuntimeException ex) {
            removeSourceAfterFailure(sourceKey, ex);
            throw ex;
        }
    }

    private void removeSourceAfterFailure(String sourceKey, RuntimeException cause) {
        try {
            studySourceStorage.delete(sourceKey);
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }

    private void generateInBackground(Long sessionId) {
        try {
            StudySession session = findStudySession(sessionId);
            StudyDocument document = studySourceStorage.read(session.getSourceKey());
            GenerationBudget budget = createGenerationBudget();
            StudyPlan plan = findOrGeneratePlan(session, document, budget);

            generateBasicChapters(sessionId, document, plan, budget);
        } catch (RuntimeException ex) {
            markGenerationFailed(sessionId, ex);
        } finally {
            studySessionRepository.releaseGeneration(sessionId);
        }
    }

    private StudyPlan findOrGeneratePlan(StudySession session, StudyDocument document, GenerationBudget budget) {
        if (session.getPlan() != null) {
            return session.getPlan();
        }
        StudyPlan plan = executeWithRetry(() -> studyGenerator.generatePlan(document, budget));
        document.validatePlan(plan);
        List<StudyChapter> chapters = plan.createChapters(session.getId());
        studySessionRepository.savePlan(session.getId(), plan, chapters, AudioVariant.BASIC);
        return plan;
    }

    private void generateBasicChapters(Long sessionId, StudyDocument document, StudyPlan plan, GenerationBudget budget) {
        List<StudyChapter> chapters = studyChapterRepository.findAllBySessionId(sessionId);
        for (StudyChapter chapter : chapters) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            StudyAudio audio = studyAudioRepository.findOrCreate(chapter.getId(), AudioVariant.BASIC);
            Optional<StudyAudio> claimedAudio = studyAudioRepository.claimGeneration(audio.getId());
            if (claimedAudio.isEmpty()) {
                continue;
            }

            StudyAudio runningAudio = claimedAudio.get();
            try {
                generateAudio(runningAudio, document, plan, chapter.getChapterOrder() - 1, budget);
            } catch (RuntimeException ex) {
                markAudioFailed(runningAudio.getId(), ex);
            }
        }
    }

    private void submitAudioGeneration(Long sessionId, StudyAudio audio) {
        Optional<StudyAudio> claimedAudio = studyAudioRepository.claimGeneration(audio.getId());
        if (claimedAudio.isEmpty()) {
            return;
        }

        StudyAudio runningAudio = claimedAudio.get();
        try {
            studyGenerationExecutor.execute(() -> generateAudioInBackground(sessionId, runningAudio));
        } catch (RuntimeException ex) {
            markAudioFailed(runningAudio.getId(), ex);
            throw ex;
        }
    }

    private void generateAudioInBackground(Long sessionId, StudyAudio audio) {
        try {
            StudySession session = findStudySession(sessionId);
            StudyDocument document = studySourceStorage.read(session.getSourceKey());
            StudyPlan plan = requireStudyPlan(session);
            StudyChapter chapter = findStudyChapter(sessionId, audio.getChapterId());

            generateAudio(audio, document, plan, chapter.getChapterOrder() - 1, createGenerationBudget());
        } catch (RuntimeException ex) {
            markAudioFailed(audio.getId(), ex);
        }
    }

    private void generateAudio(StudyAudio audio, StudyDocument document, StudyPlan plan,
                               int chapterIndex, GenerationBudget budget) {
        audio = generateExplanation(audio, document, plan, chapterIndex, budget);
        audio = reviewExplanation(audio, document, plan, chapterIndex, budget);
        audio = synthesizeAndStoreAudio(audio);

        audio.complete(audio.getFileId());
        studyAudioRepository.save(audio);
    }

    private StudyAudio generateExplanation(StudyAudio audio, StudyDocument document, StudyPlan plan,
                                           int chapterIndex, GenerationBudget budget) {
        if (audio.getStage() != GenerationStage.SCRIPT) {
            return audio;
        }

        AudioVariant variant = audio.getVariant();
        String script = executeWithRetry(
                () -> studyGenerator.generateExplanation(document, plan, chapterIndex, variant, budget));
        audio.recordScript(script);

        return studyAudioRepository.save(audio);
    }

    private StudyAudio reviewExplanation(StudyAudio audio, StudyDocument document, StudyPlan plan,
                                         int chapterIndex, GenerationBudget budget) {
        if (audio.getStage() != GenerationStage.REVIEW) {
            return audio;
        }

        String draft = audio.getScript();
        String reviewedScript = executeWithRetry(
                () -> studyGenerator.reviewExplanation(document, plan, chapterIndex, draft, budget));
        audio.approveScript(reviewedScript);

        return studyAudioRepository.save(audio);
    }

    private StudyAudio synthesizeAndStoreAudio(StudyAudio audio) {
        UUID fileId = UUID.fromString(audio.getFileId());

        // 파일 저장 직후 DB 갱신이 실패한 경우 기존 파일 재사용
        if (audioStorage.find(fileId).isPresent()) {
            return audio;
        }

        String script = audio.getScript();
        byte[] content = executeWithRetry(() -> speechSynthesizer.synthesize(script));
        if (audio.getStage() == GenerationStage.AUDIO) {
            audio.markStoragePending();
            audio = studyAudioRepository.save(audio);
        }

        executeWithRetry(() -> audioStorage.save(fileId, content));
        return audio;
    }

    private void markGenerationFailed(Long sessionId, RuntimeException cause) {
        log.warn("강의 준비 실패. 세션 ID {}, 오류 코드 {}", sessionId, failureCode(cause));
        StudySession session = findStudySession(sessionId);
        session.recordSessionFailure(failureCode(cause));
        studySessionRepository.save(session);
    }

    private void markAudioFailed(Long audioId, RuntimeException cause) {
        try {
            StudyAudio audio = findStudyAudio(audioId);
            if (audio.getStatus() != AudioStatus.GENERATING) {
                return;
            }

            if (Thread.currentThread().isInterrupted()) {
                audio.interrupt();
            } else {
                audio.fail(failureCode(cause));
            }

            studyAudioRepository.save(audio);
        } catch (RuntimeException saveFailure) {
            log.error("실패 상태 저장 실패. 음성 ID {}, 예외 유형 {}", audioId, saveFailure.getClass().getSimpleName());
        }
    }

    private <T> T executeWithRetry(Supplier<T> operation) {
        for (int attempt = 0; ; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw StudyGenerationException.interrupted();
            }

            try {
                return operation.get();
            } catch (RuntimeException ex) {
                if (!isRetryableFailure(ex) || attempt >= 2) {
                    throw ex;
                }
                waitBeforeRetry(attempt);
            }
        }
    }

    private boolean isRetryableFailure(RuntimeException cause) {
        if (cause instanceof StudyGenerationException failure) {
            return failure.isRetryable();
        }
        if (cause instanceof StudyAudioException failure && failure.isRetryable()) {
            return true;
        }
        return cause instanceof ApplicationException failure
                && failure.getFailureType() == FailureType.EXTERNAL_SERVICE_TIMEOUT;
    }

    private void waitBeforeRetry(int attempt) {
        long delayMillis = Math.min(studyGenerationSettings.retryDelayMillis() * (attempt + 1), 10000);

        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw StudyGenerationException.interrupted();
        }
    }

    private StudySession findStudySession(Long sessionId) {
        return studySessionRepository.findById(sessionId).orElseThrow(StudyNotFoundException::new);
    }

    private StudyChapter findStudyChapter(Long sessionId, Long chapterId) {
        StudyChapter chapter = studyChapterRepository.findById(chapterId).orElseThrow(StudyNotFoundException::new);
        if (!chapter.getSessionId().equals(sessionId)) {
            throw new StudyNotFoundException();
        }
        return chapter;
    }

    private StudyAudio findStudyAudio(Long audioId) {
        return studyAudioRepository.findById(audioId).orElseThrow(StudyAudioException::notFound);
    }

    private StudyPlan requireStudyPlan(StudySession session) {
        if (session.getPlan() == null) {
            throw StudyGenerationException.planRequired();
        }
        return session.getPlan();
    }

    private GenerationBudget createGenerationBudget() {
        return new GenerationBudget(studyGenerationSettings.tokenBudget(), studyGenerationSettings.callLimit());
    }

    private String failureCode(RuntimeException cause) {
        return cause instanceof ApplicationException failure ? failure.getCode() : "INTERNAL_ERROR";
    }
}
