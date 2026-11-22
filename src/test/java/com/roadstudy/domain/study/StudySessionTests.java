package com.roadstudy.domain.study;

import com.roadstudy.domain.study.exception.InvalidStudySessionException;
import com.roadstudy.domain.study.exception.InvalidStudyStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("학습 세션 도메인")
class StudySessionTests {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void rejectsMissingOriginalFileName(String name) {
        assertThatThrownBy(() -> StudySession.create(name))
                .isInstanceOf(InvalidStudySessionException.class);
    }

    @Test
    void rejectsNullOriginalFileName() {
        assertThatThrownBy(() -> StudySession.create(null))
                .isInstanceOf(InvalidStudySessionException.class)
                .hasMessageContaining("원본 파일명");
    }

    @Test
    void claimsOnlyOnceAndAllowsManualRestartAfterInterruption() {
        StudySession session = StudySession.create("lecture.pdf");
        assertThat(session.claimGeneration()).isTrue();
        assertThat(session.claimGeneration()).isFalse();
        session.interruptGeneration();
        assertThat(session.isInterrupted()).isTrue();
        assertThat(session.isGenerationActive()).isFalse();
        assertThat(session.claimGeneration()).isTrue();
        assertThat(session.isInterrupted()).isFalse();
    }

    @Test
    void preservesSourceAndPlanRatherThanSilentlyReplacingThem() {
        StudySession session = StudySession.create("lecture.pdf");
        session.attachSource("source.pdf");
        StudyPlan plan = new StudyPlan("제목", "요약", java.util.List.of(
                new StudyPlan.Chapter("장", "목표", java.util.List.of(1))), java.util.List.of());
        session.changePlan(plan);
        assertThatThrownBy(() -> session.attachSource("other.pdf")).isInstanceOf(InvalidStudyStatusTransitionException.class);
        assertThatThrownBy(() -> session.changePlan(plan)).isInstanceOf(InvalidStudyStatusTransitionException.class);
        assertThat(session.getTitle()).isEqualTo("제목");
    }
}
