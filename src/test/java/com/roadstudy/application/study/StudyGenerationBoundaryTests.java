package com.roadstudy.application.study;

import com.roadstudy.application.study.exception.StudyGenerationException;
import com.roadstudy.application.study.model.*;
import com.roadstudy.domain.study.StudyPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("학습 입력과 호출 한도")
class StudyGenerationBoundaryTests {
    @Test
    void budgetAllowsExactLimitAndRejectsNextCallBeforeItIsSent() {
        GenerationBudget budget = new GenerationBudget(100, 2);
        budget.reserve(60, 40);
        assertThat(budget.tokens()).isEqualTo(100);
        assertThatThrownBy(() -> budget.reserve(1, 1)).isInstanceOf(StudyGenerationException.class);
        assertThat(budget.calls()).isEqualTo(1);
    }

    @Test
    void actualUsageReleasesUnusedReservationButStillEnforcesCallLimit() {
        GenerationBudget budget = new GenerationBudget(100, 2);
        budget.reserve(50, 50);
        budget.settle(10, 10);
        budget.reserve(40, 40);
        assertThat(budget.tokens()).isEqualTo(100);
        budget.settle(1, 1);
        assertThatThrownBy(() -> budget.reserve(1, 1)).isInstanceOf(StudyGenerationException.class);
    }

    @Test
    void refusesInvalidBudgetAndOverflowSizedReservation() {
        assertThatThrownBy(() -> new GenerationBudget(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationBudget(1, 0)).isInstanceOf(IllegalArgumentException.class);
        GenerationBudget budget = new GenerationBudget(100, 2);
        assertThatThrownBy(() -> budget.reserve(Integer.MAX_VALUE, Integer.MAX_VALUE)).isInstanceOf(StudyGenerationException.class);
        assertThat(budget.calls()).isZero();
    }

    @Test
    void keepsBlankPagePositionButRequiresSomeReadableContent() {
        StudyDocument document = new StudyDocument(List.of(new StudyDocument.Page(1, ""), new StudyDocument.Page(2, "본문")));
        assertThat(document.pages().get(1).number()).isEqualTo(2);
        assertThatThrownBy(() -> new StudyDocument(List.of(new StudyDocument.Page(1, " "))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudyDocument(List.of(new StudyDocument.Page(2, "본문"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSourceOnUnreadablePageAndOutsideDocument() {
        StudyDocument document = new StudyDocument(List.of(new StudyDocument.Page(1, ""), new StudyDocument.Page(2, "본문")));
        for (int page : new int[]{0, 1, 3}) {
            StudyPlan plan = new StudyPlan("제목", "요약", List.of(new StudyPlan.Chapter("장", "목표", List.of(page))), List.of());
            assertThatThrownBy(() -> document.validatePlan(plan)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
