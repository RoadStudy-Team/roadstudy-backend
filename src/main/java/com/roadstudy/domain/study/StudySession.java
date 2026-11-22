package com.roadstudy.domain.study;

import com.roadstudy.domain.study.exception.InvalidStudySessionException;
import com.roadstudy.domain.study.exception.InvalidStudyStatusTransitionException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudySession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private String originalFileName;

    @Column(columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 재시도·상세 학습 시 원본 PDF와 추출 텍스트를 찾기 위한 저장소 식별자
    private String sourceKey;

    @Column(name = "plan_json", columnDefinition = "text")
    private StudyPlan plan;

    private boolean generationActive;

    private boolean interrupted;

    // 기존 실패 코드 데이터와의 매핑 유지
    @Column(name = "course_failure_code")
    private String sessionFailureCode;

    private StudySession(String originalFileName) {
        requireText(originalFileName, "원본 파일명");
        this.originalFileName = originalFileName.strip();
        this.createdAt = LocalDateTime.now();
    }

    public static StudySession create(String originalFileName) {
        return new StudySession(originalFileName);
    }

    public void attachSource(String sourceKey) {
        requireText(sourceKey, "원본 자료 저장 키");
        if (this.sourceKey != null) {
            throw new InvalidStudyStatusTransitionException();
        }

        this.sourceKey = sourceKey;
    }

    public boolean claimGeneration() {
        if (generationActive) {
            return false;
        }

        generationActive = true;
        interrupted = false;
        sessionFailureCode = null;
        return true;
    }

    public void releaseGeneration() {
        generationActive = false;
    }

    public void recordSessionFailure(String code) {
        requireText(code, "실패 코드");
        sessionFailureCode = code;
    }

    public void changePlan(StudyPlan plan) {
        if (plan == null) {
            throw new InvalidStudySessionException("강의 계획");
        }
        if (this.plan != null) {
            throw new InvalidStudyStatusTransitionException();
        }
        this.plan = plan;
        this.title = plan.title();
        this.summary = plan.summary();
    }

    public void interruptGeneration() {
        if (generationActive) {
            generationActive = false;
            interrupted = true;
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidStudySessionException(field);
        }
    }
}
