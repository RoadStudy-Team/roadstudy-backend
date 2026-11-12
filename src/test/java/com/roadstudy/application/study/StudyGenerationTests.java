package com.roadstudy.application.study;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import com.roadstudy.application.study.exception.StudyExplanationException;
import com.roadstudy.application.study.exception.StudyNotFoundException;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudyStatus;
import com.roadstudy.global.exception.FailureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("학습 자동 생성")
class StudyGenerationTests {
    private static final byte[] DOCUMENT = {1};
    private static final byte[] AUDIO_BYTES = {(byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4};
    private static final String TEXT = "학습 자료";
    private static final StudyExplanation EXPLANATION = new StudyExplanation("제목", "요약", "설명 대본");
    private static final Path AUDIO_PATH = Path.of("storage", "audio", "lecture.mp3");

    private final DocumentReader reader = mock(DocumentReader.class);
    private final StudyGenerator generator = mock(StudyGenerator.class);
    private final SpeechSynthesizer synthesizer = mock(SpeechSynthesizer.class);
    private final AudioStorage storage = mock(AudioStorage.class);
    private final InMemoryStudyRepository repository = new InMemoryStudyRepository();
    // 넘겨받은 작업을 바로 실행하지 않고 모아 두어, 응답 시점과 백그라운드 완료 시점을 나눠 검증한다.
    private final Deque<Runnable> backgroundTasks = new ArrayDeque<>();
    private final StudyModifyService service = new StudyModifyService(
            reader, generator, repository, synthesizer, storage, backgroundTasks::add);
    private final StudyQueryService queryService = new StudyQueryService(storage, repository);

    @BeforeEach
    void setUp() {
        when(reader.read(DOCUMENT)).thenReturn(new ExtractedStudyText(1, TEXT));
        when(generator.generate(TEXT)).thenReturn(EXPLANATION);
        when(synthesizer.synthesize(EXPLANATION.explanationScript())).thenReturn(AUDIO_BYTES);
        when(storage.save(any(UUID.class), eq(AUDIO_BYTES)))
                .thenAnswer(call -> new StudyAudio(call.getArgument(0), AUDIO_PATH));
    }

    @Test
    @DisplayName("입력 검증을 마치면 처리 중인 세션을 저장해 반환하고 생성은 백그라운드로 넘긴다")
    void startsGenerationInBackground() throws Exception {
        StudySession session = start();

        assertThat(session.getId()).isNotNull();
        assertThat(session.getOriginalFileName()).isEqualTo("lecture.pdf");
        assertThat(session.getStatus()).isEqualTo(StudyStatus.PROCESSING);
        assertThat(backgroundTasks).hasSize(1);
        verifyNoInteractions(generator, synthesizer, storage);
    }

    @Test
    @DisplayName("백그라운드 생성이 끝나면 대본과 음성 경로를 저장하고 세션을 완료한다")
    void completesSessionInBackground() throws Exception {
        Long id = start().getId();

        runBackgroundTask();

        StudySession session = queryService.findById(id);
        assertThat(session.getStatus()).isEqualTo(StudyStatus.COMPLETED);
        assertThat(session.getTitle()).isEqualTo(EXPLANATION.title());
        assertThat(session.getSummary()).isEqualTo(EXPLANATION.summary());
        assertThat(session.getExplanationScript()).isEqualTo(EXPLANATION.explanationScript());
        assertThat(session.getAudioPath()).isEqualTo(AUDIO_PATH.toString());
        assertThat(repository.savedStatuses())
                .containsExactly(StudyStatus.PROCESSING, StudyStatus.PROCESSING, StudyStatus.COMPLETED);
    }

    @Test
    @DisplayName("추출한 텍스트가 6만 자를 넘으면 세션을 만들지 않고 즉시 거부한다")
    void rejectsTooLongTextBeforeCreatingSession() {
        when(reader.read(DOCUMENT)).thenReturn(new ExtractedStudyText(1, "가".repeat(60_001)));

        assertThatExceptionOfType(StudyExplanationException.class)
                .isThrownBy(this::start)
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("STUDY_TEXT_TOO_LONG"));
        assertThat(repository.savedStatuses()).isEmpty();
        assertThat(backgroundTasks).isEmpty();
    }

    @Test
    @DisplayName("대본 생성이 실패하면 음성을 만들지 않고 세션을 실패 처리한다")
    void failsSessionWhenExplanationFails() throws Exception {
        when(generator.generate(TEXT)).thenThrow(StudyExplanationException.generationFailed());
        Long id = start().getId();

        runBackgroundTask();

        StudySession session = queryService.findById(id);
        assertThat(session.getStatus()).isEqualTo(StudyStatus.FAILED);
        assertThat(session.getExplanationScript()).isNull();
        assertThat(repository.savedStatuses()).containsExactly(StudyStatus.PROCESSING, StudyStatus.FAILED);
        verifyNoInteractions(synthesizer, storage);
    }

    @Test
    @DisplayName("대본이 음성 합성 제한을 넘으면 생성된 대본은 남기고 세션을 실패 처리한다")
    void failsSessionWhenScriptExceedsSpeechLimit() throws Exception {
        // 한글 1,667자는 5,001바이트로 음성 합성 제한을 1바이트 넘는다.
        String script = "가".repeat(10_001);
        when(generator.generate(TEXT)).thenReturn(new StudyExplanation("제목", "요약", script));
        Long id = start().getId();

        runBackgroundTask();

        StudySession session = queryService.findById(id);
        assertThat(session.getStatus()).isEqualTo(StudyStatus.FAILED);
        assertThat(session.getExplanationScript()).isEqualTo(script);
        assertThat(session.getAudioPath()).isNull();
        assertThat(repository.savedStatuses())
                .containsExactly(StudyStatus.PROCESSING, StudyStatus.PROCESSING, StudyStatus.FAILED);
        verifyNoInteractions(synthesizer, storage);
    }

    @Test
    @DisplayName("예상하지 못한 예외가 나도 세션을 실패 처리하고 백그라운드 작업 밖으로 던지지 않는다")
    void failsSessionOnUnexpectedException() throws Exception {
        when(synthesizer.synthesize(EXPLANATION.explanationScript())).thenThrow(new IllegalStateException("예상하지 못한 오류"));
        Long id = start().getId();

        assertThatCode(this::runBackgroundTask).doesNotThrowAnyException();

        assertThat(queryService.findById(id).getStatus()).isEqualTo(StudyStatus.FAILED);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("백그라운드 작업을 맡기지 못하면 세션을 실패 처리하고 예외를 전달한다")
    void failsSessionWhenBackgroundTaskIsRejected() {
        var rejectingService = new StudyModifyService(reader, generator, repository, synthesizer, storage, task -> {
            throw new RejectedExecutionException();
        });

        assertThatExceptionOfType(RejectedExecutionException.class)
                .isThrownBy(() -> rejectingService.start("lecture.pdf", new ByteArrayInputStream(DOCUMENT)));
        assertThat(repository.savedStatuses()).containsExactly(StudyStatus.PROCESSING, StudyStatus.FAILED);
        verifyNoInteractions(generator, synthesizer, storage);
    }

    @Test
    @DisplayName("없는 학습 세션을 조회하면 찾을 수 없음 예외를 던진다")
    void rejectsUnknownStudy() {
        assertThatExceptionOfType(StudyNotFoundException.class)
                .isThrownBy(() -> queryService.findById(404L))
                .satisfies(ex -> {
                    assertThat(ex.getCode()).isEqualTo("STUDY_NOT_FOUND");
                    assertThat(ex.getFailureType()).isEqualTo(FailureType.NOT_FOUND);
                });
    }

    private StudySession start() throws IOException {
        return service.start("lecture.pdf", new ByteArrayInputStream(DOCUMENT));
    }

    private void runBackgroundTask() {
        backgroundTasks.remove().run();
    }
}
