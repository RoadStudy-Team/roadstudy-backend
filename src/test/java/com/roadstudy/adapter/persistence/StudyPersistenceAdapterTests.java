package com.roadstudy.adapter.persistence;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.UUID;

import com.roadstudy.application.study.ExtractedStudyText;
import com.roadstudy.application.study.StudyAudio;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.application.study.StudyExplanation;
import com.roadstudy.application.study.StudyModifyService;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudySessionCreateRequest;
import com.roadstudy.domain.study.StudyStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(StudyPersistenceAdapter.class)
@DisplayName("학습 세션 JPA 저장 어댑터")
class StudyPersistenceAdapterTests {
    @Autowired StudyRepository studyRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("학습 세션의 초기 상태와 실패 상태를 저장하고 조회한다")
    void savesAndLoadsSessionStatus() {
        var processing = studyRepository.save(StudySession.create(new StudySessionCreateRequest("lecture.pdf")));
        Long id = processing.getId();
        assertThat(id).isNotNull();

        entityManager.flush();
        entityManager.clear();
        var loaded = studyRepository.findById(id).orElseThrow();
        var createdAt = loaded.getCreatedAt();
        assertThat(createdAt).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(StudyStatus.PROCESSING);

        entityManager.detach(loaded);
        loaded.fail();
        studyRepository.save(loaded);

        entityManager.flush();
        entityManager.clear();
        var failed = studyRepository.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(StudyStatus.FAILED);
        assertThat(failed.getOriginalFileName()).isEqualTo("lecture.pdf");
        assertThat(failed.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("생성한 학습 대본을 저장하고 세션 ID로 다시 조회한다")
    void savesAndReloadsGeneratedExplanation() throws Exception {
        String script = "한국어 학습 대본입니다. ".repeat(400);
        var service = new StudyModifyService(
                content -> new ExtractedStudyText(1, "학습 자료"),
                text -> new StudyExplanation("학습 제목", "학습 요약", script),
                studyRepository,
                mock(SpeechSynthesizer.class), mock(AudioStorage.class), Runnable::run);

        Long id = service.generate("lecture.pdf", new ByteArrayInputStream(new byte[]{1})).getId();
        assertThat(id).isNotNull();
        entityManager.flush();
        entityManager.clear();

        StudySession session = studyRepository.findById(id).orElseThrow();
        assertThat(session.getOriginalFileName()).isEqualTo("lecture.pdf");
        assertThat(session.getTitle()).isEqualTo("학습 제목");
        assertThat(session.getSummary()).isEqualTo("학습 요약");
        assertThat(session.getExplanationScript()).isEqualTo(script);
        assertThat(session.getStatus()).isEqualTo(StudyStatus.PROCESSING);
    }

    @Test
    @DisplayName("자동 생성이 끝난 학습 세션의 완료 상태와 음성 경로를 저장하고 다시 조회한다")
    void savesAndReloadsCompletedGeneration() throws Exception {
        Path audioPath = Path.of("storage", "audio", "lecture.mp3");
        var storage = mock(AudioStorage.class);
        when(storage.save(any(UUID.class), any(byte[].class)))
                .thenAnswer(call -> new StudyAudio(call.getArgument(0), audioPath));
        var service = new StudyModifyService(
                content -> new ExtractedStudyText(1, "학습 자료"),
                text -> new StudyExplanation("학습 제목", "학습 요약", "학습 대본"),
                studyRepository,
                script -> new byte[]{1}, storage, Runnable::run);

        Long id = service.start("lecture.pdf", new ByteArrayInputStream(new byte[]{1})).getId();
        entityManager.flush();
        entityManager.clear();

        StudySession session = studyRepository.findById(id).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(StudyStatus.COMPLETED);
        assertThat(session.getExplanationScript()).isEqualTo("학습 대본");
        assertThat(session.getAudioPath()).isEqualTo(audioPath.toString());
    }
}
