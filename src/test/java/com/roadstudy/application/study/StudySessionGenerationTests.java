package com.roadstudy.application.study;

import com.roadstudy.domain.study.request.StudyChapterCreateRequest;
import com.roadstudy.domain.study.request.StudyAudioCreateRequest;
import com.roadstudy.domain.study.enums.GenerationStage;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.application.study.exception.StudyGenerationException;
import com.roadstudy.application.study.model.*;
import com.roadstudy.application.study.model.StudyGenerationSettings;
import com.roadstudy.application.study.port.out.*;
import com.roadstudy.application.study.service.StudyModifyService;
import com.roadstudy.domain.study.*;
import com.roadstudy.domain.study.StudyAudio;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("챕터 학습 생성 서비스")
class StudySessionGenerationTests {
    StudySessionRepository sessions = mock(StudySessionRepository.class);
    StudyChapterRepository chapters = mock(StudyChapterRepository.class);
    StudyAudioRepository audios = mock(StudyAudioRepository.class);
    StudyGenerator generator = mock(StudyGenerator.class);
    StudySourceStorage sources = mock(StudySourceStorage.class);
    SpeechSynthesizer tts = mock(SpeechSynthesizer.class);
    AudioStorage files = mock(AudioStorage.class);
    Deque<Runnable> tasks = new ArrayDeque<>();
    StudyModifyService service;
    StudySession session;
    StudyChapter first;
    StudyChapter second;
    StudyAudio audio1;
    StudyAudio audio2;
    StudyDocument document = new StudyDocument(List.of(new StudyDocument.Page(1, "source")));
    StudyPlan plan = new StudyPlan("제목", "요약", List.of(
            new StudyPlan.Chapter("1장", "목표1", List.of(1)),
            new StudyPlan.Chapter("2장", "목표2", List.of(1))), List.of());

    @BeforeEach
    void setup() throws Exception {
        session = StudySession.create("test.pdf");
        ReflectionTestUtils.setField(session, "id", 1L);
        session.attachSource("source.pdf");
        session.changePlan(plan);
        first = chapter(10L, 1);
        second = chapter(11L, 2);
        audio1 = audio(20L, 10L, AudioVariant.BASIC);
        audio2 = audio(21L, 11L, AudioVariant.BASIC);
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        when(sessions.claimGeneration(1L)).thenReturn(true);
        when(sources.read(anyString())).thenReturn(document);
        when(chapters.findAllBySessionId(1L)).thenReturn(List.of(first, second));
        when(chapters.findById(10L)).thenReturn(Optional.of(first));
        when(chapters.findById(11L)).thenReturn(Optional.of(second));
        when(audios.findOrCreate(10L, AudioVariant.BASIC)).thenReturn(audio1);
        when(audios.findOrCreate(11L, AudioVariant.BASIC)).thenReturn(audio2);
        when(audios.findById(20L)).thenReturn(Optional.of(audio1));
        when(audios.findById(21L)).thenReturn(Optional.of(audio2));
        when(audios.claimGeneration(anyLong())).thenAnswer(call -> {
            StudyAudio audio = call.getArgument(0).equals(20L) ? audio1 : audio2;
            if (audio.getStatus() == AudioStatus.READY || audio.getStatus() == AudioStatus.GENERATING) return Optional.empty();
            audio.begin();
            return Optional.of(audio);
        });
        when(audios.save(any())).thenAnswer(call -> call.getArgument(0));
        when(generator.generateExplanation(any(), any(), anyInt(), any(), any())).thenReturn("초안");
        when(generator.reviewExplanation(any(), any(), anyInt(), anyString(), any())).thenReturn("완성 대본");
        when(tts.synthesize(anyString())).thenReturn(new byte[]{1, 2, 3});
        when(files.save(any(), any())).thenAnswer(call -> new StoredAudio(
                call.getArgument(0), Path.of("storage/audio/" + call.getArgument(0) + ".mp3")));
        service = service(tasks::add);
    }

    @Test
    void completesChaptersInOrderAndPreservesReviewedScripts() {
        service.retryGeneration(1L);
        assertThat(tasks).hasSize(1);
        verifyNoInteractions(generator);
        tasks.remove().run();
        assertThat(audio1.getStatus()).isEqualTo(AudioStatus.READY);
        assertThat(audio2.getStatus()).isEqualTo(AudioStatus.READY);
        InOrder order = inOrder(generator);
        order.verify(generator).generateExplanation(any(), any(), eq(0), eq(AudioVariant.BASIC), any());
        order.verify(generator).reviewExplanation(any(), any(), eq(0), eq("초안"), any());
        order.verify(generator).generateExplanation(any(), any(), eq(1), eq(AudioVariant.BASIC), any());
        assertThat(audio1.getScript()).isEqualTo("완성 대본");
        verify(sessions).releaseGeneration(1L);
    }

    @Test
    void retriesTransientTtsTwiceThenContinuesOtherChapters() {
        when(tts.synthesize(anyString())).thenThrow(com.roadstudy.application.study.exception.StudyAudioException.generationTimedOut())
                .thenThrow(com.roadstudy.application.study.exception.StudyAudioException.generationTimedOut())
                .thenThrow(com.roadstudy.application.study.exception.StudyAudioException.generationTimedOut()).thenReturn(new byte[]{1});
        service.retryGeneration(1L);
        tasks.remove().run();
        assertThat(audio1.getStatus()).isEqualTo(AudioStatus.FAILED);
        assertThat(audio1.getStage()).isEqualTo(GenerationStage.AUDIO);
        assertThat(audio1.getScript()).isEqualTo("완성 대본");
        assertThat(audio2.getStatus()).isEqualTo(AudioStatus.READY);
        verify(tts, times(4)).synthesize("완성 대본");
        verify(generator, times(2)).generateExplanation(any(), any(), anyInt(), any(), any());
    }

    @Test
    void manualRetrySkipsReadyAudioAndDoesNotRegenerateReviewedScript() {
        audio1.begin(); audio1.recordScript("초안"); audio1.approveScript("보존 대본"); audio1.fail("TTS_TIMEOUT");
        audio2.begin(); audio2.recordScript("초안"); audio2.approveScript("대본"); audio2.complete(UUID.randomUUID().toString());
        service.retryGeneration(1L);
        tasks.remove().run();
        verifyNoInteractions(generator);
        verify(tts).synthesize("보존 대본");
        assertThat(audio1.getStatus()).isEqualTo(AudioStatus.READY);
    }

    @Test
    void doesNotRetryAuthenticationFailure() {
        when(generator.generateExplanation(any(), any(), eq(0), any(), any())).thenThrow(StudyGenerationException.externalServiceFailed(401));
        service.retryGeneration(1L);
        tasks.remove().run();
        verify(generator, times(1)).generateExplanation(any(), any(), eq(0), any(), any());
        assertThat(audio1.getFailureCode()).isEqualTo("OPENAI_HTTP_401");
        assertThat(audio2.getStatus()).isEqualTo(AudioStatus.READY);
    }

    @Test
    void duplicateSessionRequestDoesNotScheduleAnotherTask() {
        when(sessions.claimGeneration(1L)).thenReturn(false);
        service.retryGeneration(1L);
        assertThat(tasks).isEmpty();
    }

    @Test
    void rejectedTaskReleasesClaimForManualRetry() {
        service = service(task -> { throw new java.util.concurrent.RejectedExecutionException(); });
        assertThatThrownBy(() -> service.retryGeneration(1L)).isInstanceOf(RuntimeException.class);
        verify(sessions).releaseGeneration(1L);
    }

    @Test
    void removesUnusableUploadAndDoesNotCreateSession() {
        when(sources.save(any(), any())).thenReturn("bad.pdf");
        when(sources.read("bad.pdf")).thenThrow(com.roadstudy.application.study.exception.PdfExtractionException.emptyText());
        assertThatThrownBy(() -> service.generateStudyAsync("test.pdf", new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(RuntimeException.class);
        verify(sources).delete("bad.pdf");
        verify(sessions, never()).save(any());
        assertThat(tasks).isEmpty();
    }

    @Test
    void removesSourceWhenSessionCannotBeSaved() {
        when(sources.save(any(), any())).thenReturn("source.pdf");
        when(sessions.save(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.generateStudyAsync("test.pdf", new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(IllegalStateException.class);
        verify(sources).delete("source.pdf");
        assertThat(tasks).isEmpty();
    }

    @Test
    void rejectsEmptyAndOversizedFilesBeforeStorageOrPaidCalls() {
        assertThatThrownBy(() -> service.generateStudyAsync("test.pdf", new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(com.roadstudy.application.study.exception.PdfExtractionException.class);
        assertThatThrownBy(() -> service.generateStudyAsync("test.pdf",
                new ByteArrayInputStream(new byte[50 * 1024 * 1024 + 1])))
                .isInstanceOf(com.roadstudy.application.study.exception.PdfExtractionException.class);
        verifyNoInteractions(sources, generator, tts);
    }

    @Test
    void translatesCheckedReadFailureBeforeItEscapesUseCase() {
        java.io.InputStream broken = new java.io.InputStream() {
            @Override public int read() throws java.io.IOException { throw new java.io.IOException("read failed"); }
        };
        assertThatThrownBy(() -> service.generateStudyAsync("test.pdf", broken))
                .isInstanceOfSatisfying(com.roadstudy.application.study.exception.PdfExtractionException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PDF_READ_FAILED"));
        verifyNoInteractions(sources, generator, tts);
    }

    @Test
    void doesNotRegenerateTtsWhenFileWasSavedBeforeDatabaseFailure() {
        audio1.begin(); audio1.recordScript("초안"); audio1.approveScript("보존 대본");
        audio1.markStoragePending(); audio1.fail("DATABASE_FAILURE");
        audio2.begin(); audio2.recordScript("초안"); audio2.approveScript("대본");
        audio2.complete(UUID.randomUUID().toString());
        when(files.find(UUID.fromString(audio1.getFileId()))).thenReturn(Optional.of(
                new StoredAudio(UUID.fromString(audio1.getFileId()), Path.of("audio.mp3"))));
        service.retryGeneration(1L);
        tasks.remove().run();
        verifyNoInteractions(generator, tts);
        verify(files, never()).save(any(), any());
        assertThat(audio1.getStatus()).isEqualTo(AudioStatus.READY);
    }

    @Test
    void rejectsChapterFromAnotherSession() {
        assertThatThrownBy(() -> service.generateDetailedAudioAsync(2L, 10L))
                .isInstanceOf(com.roadstudy.application.study.exception.StudyNotFoundException.class);
        assertThat(tasks).isEmpty();
    }

    private StudyModifyService service(Executor executor) {
        return new StudyModifyService(sessions, chapters, audios, sources, generator, tts, files, executor,
                new StudyGenerationSettings(300000, 80, 0));
    }

    private StudyChapter chapter(Long id, int order) {
        StudyChapterCreateRequest request = new StudyChapterCreateRequest(1L, order, "장", "목표", "1");
        StudyChapter chapter = StudyChapter.create(request);
        ReflectionTestUtils.setField(chapter, "id", id);
        return chapter;
    }

    private StudyAudio audio(Long id, Long chapterId, AudioVariant variant) {
        StudyAudioCreateRequest audioRequest = new StudyAudioCreateRequest(chapterId, variant);
        StudyAudio audio = StudyAudio.create(audioRequest);
        ReflectionTestUtils.setField(audio, "id", id);
        return audio;
    }
}
