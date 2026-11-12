package com.roadstudy.domain.study;

import com.roadstudy.domain.study.exception.InvalidStudySessionException;
import com.roadstudy.domain.study.exception.InvalidStudyStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("학습 세션 도메인")
class StudySessionTests {
    @Test
    @DisplayName("원본 파일명이 비어 있으면 학습 세션 생성을 거부한다")
    void rejectsMissingOriginalFileName() {
        assertThatThrownBy(() -> StudySession.create(new StudySessionCreateRequest(" ")))
                .isInstanceOf(InvalidStudySessionException.class);
    }

    @Test
    @DisplayName("이미 실패한 학습 세션의 실패 처리를 거부한다")
    void rejectsFailingAnAlreadyFailedSession() {
        var study = StudySession.create(new StudySessionCreateRequest("lecture.pdf"));
        study.fail();

        assertThatThrownBy(study::fail)
                .isInstanceOf(InvalidStudyStatusTransitionException.class);
        assertThat(study.getStatus()).isEqualTo(StudyStatus.FAILED);
    }
}
