package com.roadstudy.domain.study;

import com.roadstudy.domain.study.request.StudyChapterCreateRequest;
import com.roadstudy.domain.study.exception.InvalidStudyChapterException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "study_chapters", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "chapter_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyChapter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private StudySession session;

    @Column(name = "chapter_order", nullable = false)
    private int chapterOrder;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String objective;

    @Column(nullable = false, columnDefinition = "text")
    private String sourceReferences;

    private StudyChapter(Long sessionId, int order, String title, String objective, String references) {
        if (sessionId == null || sessionId <= 0 || order <= 0) {
            throw new InvalidStudyChapterException("학습 세션 ID 또는 챕터 순서");
        }

        requireText(title, "챕터 제목");
        requireText(objective, "학습 목표");
        requireText(references, "원문 근거");
        this.sessionId = sessionId;
        this.chapterOrder = order;
        this.title = title.strip();
        this.objective = objective.strip();
        this.sourceReferences = references;
    }

    public static StudyChapter create(StudyChapterCreateRequest request) {
        if (request == null) {
            throw new InvalidStudyChapterException("챕터 생성 정보");
        }

        return new StudyChapter(request.sessionId(), request.order(), request.title(),
                request.objective(), request.references());
    }

    private static void requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new InvalidStudyChapterException(field);
        }
    }
}
