package com.roadstudy.adapter.persistence;

import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.adapter.persistence.converter.StudyPlanConverter;

import com.roadstudy.application.study.port.out.StudySessionRepository;
import com.roadstudy.application.study.port.out.StudyChapterRepository;
import com.roadstudy.application.study.port.out.StudyAudioRepository;
import com.roadstudy.domain.study.*;
import com.roadstudy.domain.study.StudyPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StudySessionPersistenceAdapter.class, StudyChapterPersistenceAdapter.class, StudyAudioPersistenceAdapter.class, StudyPlanConverter.class, JacksonAutoConfiguration.class})
@DisplayName("학습 챕터 JPA 저장")
class StudySessionPersistenceTests {
    @Autowired StudySessionRepository studySessionRepository;
    @Autowired StudyChapterRepository studyChapterRepository;
    @Autowired StudyAudioRepository studyAudioRepository;
    @Autowired StudySessionJpaRepository sessions;

    @Test
    void createsPlanAndEnforcesOneAudioPerVariant() {
        StudySession session = sessions.saveAndFlush(StudySession.create("test.pdf"));
        StudyPlan plan = new StudyPlan("제목", "요약",
                List.of(new StudyPlan.Chapter("첫 장", "학습 목표", List.of(1))), List.of());
        studySessionRepository.savePlan(session.getId(), plan, plan.createChapters(session.getId()), AudioVariant.BASIC);
        StudyChapter chapter = studyChapterRepository.findAllBySessionId(session.getId()).getFirst();
        StudyAudio first = studyAudioRepository.findOrCreate(chapter.getId(), AudioVariant.BASIC);
        assertThat(studyAudioRepository.findOrCreate(chapter.getId(), AudioVariant.BASIC).getId()).isEqualTo(first.getId());
        assertThat(studyAudioRepository.findOrCreate(chapter.getId(), AudioVariant.DETAILED).getId()).isNotEqualTo(first.getId());
        assertThat(studyAudioRepository.claimGeneration(first.getId())).isPresent();
        assertThat(studyAudioRepository.claimGeneration(first.getId())).isEmpty();
    }

    @Test
    void restartPreservesScriptAndRequiresManualRetry() {
        StudySession session = sessions.saveAndFlush(StudySession.create("test.pdf"));
        StudyPlan plan = new StudyPlan("제목", "요약",
                List.of(new StudyPlan.Chapter("첫 장", "목표", List.of(1))), List.of());
        studySessionRepository.savePlan(session.getId(), plan, plan.createChapters(session.getId()), AudioVariant.BASIC);
        Long chapterId = studyChapterRepository.findAllBySessionId(session.getId()).getFirst().getId();
        StudyAudio audio = studyAudioRepository.findOrCreate(chapterId, AudioVariant.BASIC);
        audio = studyAudioRepository.claimGeneration(audio.getId()).orElseThrow();
        audio.recordScript("초안");
        audio.approveScript("완성 대본");
        studyAudioRepository.save(audio);
        assertThat(studySessionRepository.claimGeneration(session.getId())).isTrue();
        assertThat(studySessionRepository.claimGeneration(session.getId())).isFalse();
        studySessionRepository.interruptRunning();
        studyAudioRepository.interruptRunning();
        StudyAudio interrupted = studyAudioRepository.findById(audio.getId()).orElseThrow();
        assertThat(interrupted.getStatus()).isEqualTo(AudioStatus.INTERRUPTED);
        assertThat(interrupted.getScript()).isEqualTo("완성 대본");
        assertThat(studySessionRepository.claimGeneration(session.getId())).isTrue();
        assertThat(studyAudioRepository.claimGeneration(audio.getId())).isPresent();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("기본 음성 저장 실패 시 계획과 챕터도 함께 롤백한다")
    void rollsBackPlanAndChaptersWhenInitialAudioCannotBeSaved() {
        StudySession session = sessions.saveAndFlush(StudySession.create("rollback.pdf"));
        StudyPlan plan = new StudyPlan("제목", "요약",
                List.of(new StudyPlan.Chapter("장", "목표", List.of(1))), List.of());

        assertThatThrownBy(() -> studySessionRepository.savePlan(session.getId(), plan, plan.createChapters(session.getId()), null))
                .isInstanceOf(com.roadstudy.domain.study.exception.InvalidStudyAudioException.class);

        assertThat(sessions.findById(session.getId()).orElseThrow().getPlan()).isNull();
        assertThat(studyChapterRepository.findAllBySessionId(session.getId())).isEmpty();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentClaimsStartOnlyOneJob() throws Exception {
        StudySession session = sessions.saveAndFlush(StudySession.create("concurrent.pdf"));
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> claim = () -> { start.await(); return studySessionRepository.claimGeneration(session.getId()); };
            java.util.concurrent.Future<Boolean> first = executor.submit(claim);
            java.util.concurrent.Future<Boolean> second = executor.submit(claim);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }
}
