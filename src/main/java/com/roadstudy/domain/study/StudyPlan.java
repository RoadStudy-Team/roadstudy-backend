package com.roadstudy.domain.study;

import com.roadstudy.domain.study.request.StudyChapterCreateRequest;
import com.roadstudy.domain.study.exception.InvalidStudyPlanException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record StudyPlan(String title, String summary, List<Chapter> chapters, List<String> notices) {
    public StudyPlan {
        requireText(title, "학습 제목");
        requireText(summary, "학습 요약");

        if (chapters == null || chapters.isEmpty()) {
            throw new InvalidStudyPlanException("챕터 목록");
        }

        chapters = List.copyOf(chapters);
        notices = notices == null ? List.of() : List.copyOf(notices);
        for (Chapter chapter : chapters) {
            requireText(chapter.title(), "챕터 제목");
            requireText(chapter.objective(), "학습 목표");

            if (chapter.pages().isEmpty()) {
                throw new InvalidStudyPlanException("원문 근거 페이지");
            }
        }
    }

    public List<StudyChapter> createChapters(Long sessionId) {
        List<StudyChapter> studyChapters = new ArrayList<>();
        int order = 1;

        for (Chapter chapter : chapters) {
            String references = chapter.pages().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            StudyChapterCreateRequest request = new StudyChapterCreateRequest(
                    sessionId, order, chapter.title(), chapter.objective(), references);
            StudyChapter studyChapter = StudyChapter.create(request);
            studyChapters.add(studyChapter);
            order++;
        }

        return List.copyOf(studyChapters);
    }

    private static void requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new InvalidStudyPlanException(field);
        }
    }

    public record Chapter(String title, String objective, List<Integer> pages) {
        public Chapter {
            pages = pages == null ? List.of() : List.copyOf(pages);
        }
    }
}
