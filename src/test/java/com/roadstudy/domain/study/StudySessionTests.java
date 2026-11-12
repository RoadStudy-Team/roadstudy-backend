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

    @Test
    @DisplayName("대본이 준비된 처리 중 세션에 음성 경로를 기록하고 완료한다")
    void completesSessionWithAudioPath() {
        var study = StudySession.create(new StudySessionCreateRequest("lecture.pdf"));
        study.changeExplanation("제목", "요약", "설명 대본");

        study.complete("storage/audio/lecture.mp3");

        assertThat(study.getStatus()).isEqualTo(StudyStatus.COMPLETED);
        assertThat(study.getAudioPath()).isEqualTo("storage/audio/lecture.mp3");
    }

    @Test
    @DisplayName("대본이 없는 학습 세션의 완료 처리를 거부한다")
    void rejectsCompletingWithoutExplanation() {
        var study = StudySession.create(new StudySessionCreateRequest("lecture.pdf"));

        assertThatThrownBy(() -> study.complete("storage/audio/lecture.mp3"))
                .isInstanceOf(InvalidStudySessionException.class);
        assertThat(study.getStatus()).isEqualTo(StudyStatus.PROCESSING);
        assertThat(study.getAudioPath()).isNull();
    }

    @Test
    @DisplayName("음성 경로가 비어 있으면 학습 세션의 완료 처리를 거부한다")
    void rejectsCompletingWithoutAudioPath() {
        var study = StudySession.create(new StudySessionCreateRequest("lecture.pdf"));
        study.changeExplanation("제목", "요약", "설명 대본");

        assertThatThrownBy(() -> study.complete(" "))
                .isInstanceOf(InvalidStudySessionException.class);
        assertThat(study.getStatus()).isEqualTo(StudyStatus.PROCESSING);
    }

    @Test
    @DisplayName("이미 완료하거나 실패한 학습 세션의 완료 처리를 거부한다")
    void rejectsCompletingAFinishedSession() {
        var completed = StudySession.create(new StudySessionCreateRequest("lecture.pdf"));
        completed.changeExplanation("제목", "요약", "설명 대본");
        completed.complete("storage/audio/lecture.mp3");
        var failed = StudySession.create(new StudySessionCreateRequest("lecture.pdf"));
        failed.changeExplanation("제목", "요약", "설명 대본");
        failed.fail();

        assertThatThrownBy(() -> completed.complete("storage/audio/other.mp3"))
                .isInstanceOf(InvalidStudyStatusTransitionException.class);
        assertThatThrownBy(() -> failed.complete("storage/audio/lecture.mp3"))
                .isInstanceOf(InvalidStudyStatusTransitionException.class);
        assertThat(completed.getAudioPath()).isEqualTo("storage/audio/lecture.mp3");
        assertThat(failed.getStatus()).isEqualTo(StudyStatus.FAILED);
    }
}
