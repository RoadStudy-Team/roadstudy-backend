package com.roadstudy.domain.study;

import java.time.LocalDateTime;

import com.roadstudy.domain.study.exception.InvalidStudySessionException;
import com.roadstudy.domain.study.exception.InvalidStudyStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "study_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudySession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String originalFileName;

    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(columnDefinition = "text")
    private String explanationScript;

    private String audioPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudyStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private StudySession(String originalFileName) {
        requireText(originalFileName, "originalFileName");
        this.originalFileName = originalFileName;
        this.createdAt = LocalDateTime.now();
        this.status = StudyStatus.PROCESSING;
    }

    public static StudySession create(StudySessionCreateRequest request) {
        return new StudySession(request.originalFileName());
    }

    public void changeExplanation(String title, String summary, String explanationScript) {
        requireProcessing();
        requireText(title, "title");
        requireText(summary, "summary");
        requireText(explanationScript, "explanationScript");

        this.title = title;
        this.summary = summary;
        this.explanationScript = explanationScript;
    }

    public void complete(String audioPath) {
        requireProcessing();
        requireText(explanationScript, "explanationScript");
        requireText(audioPath, "audioPath");

        this.audioPath = audioPath;
        this.status = StudyStatus.COMPLETED;
    }

    public void fail() {
        requireProcessing();
        this.status = StudyStatus.FAILED;
    }

    private void requireProcessing() {
        if (status != StudyStatus.PROCESSING) {
            throw new InvalidStudyStatusTransitionException();
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidStudySessionException(field);
        }
    }
}
