package com.roadstudy.adapter.persistence;

import com.roadstudy.adapter.persistence.converter.StudyPlanConverter;

import com.roadstudy.application.study.port.out.StudySessionRepository;
import com.roadstudy.application.study.port.out.StudyChapterRepository;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.StudyPlan;
import com.roadstudy.domain.study.StudySession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({StudySessionPersistenceAdapter.class, StudyChapterPersistenceAdapter.class, StudyPlanConverter.class, JacksonAutoConfiguration.class})
@DisplayName("강의 계획 저장 형식 분리")
class StudyPlanPersistenceTests {
    @Autowired StudySessionJpaRepository studyRepository;
    @Autowired StudySessionRepository studySessionRepository;
    @Autowired StudyChapterRepository studyChapterRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("도메인 강의 계획을 저장하고 같은 값으로 복원한다")
    void savesAndReloadsTypedPlan() {
        StudySession session = studyRepository.saveAndFlush(
                StudySession.create("lecture.pdf"));
        StudyPlan plan = new StudyPlan("제목", "요약",
                List.of(new StudyPlan.Chapter("스레드", "실행 흐름 이해", List.of(1))),
                List.of("검증 내역"));

        studySessionRepository.savePlan(session.getId(), plan, plan.createChapters(session.getId()), AudioVariant.BASIC);
        entityManager.flush();
        entityManager.clear();

        StudySession saved = studyRepository.findById(session.getId()).orElseThrow();
        assertThat(saved.getPlan()).isEqualTo(plan);
        assertThat(saved.getTitle()).isEqualTo(plan.title());
        assertThat(studyChapterRepository.findAllBySessionId(session.getId())).hasSize(1);
    }
}
