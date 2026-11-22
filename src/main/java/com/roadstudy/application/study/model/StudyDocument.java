package com.roadstudy.application.study.model;

import com.roadstudy.domain.study.StudyPlan;

import java.util.List;

public record StudyDocument(List<Page> pages) {

    public StudyDocument {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("학습 자료의 페이지 정보가 필요합니다.");
        }

        pages = List.copyOf(pages);
        int expected = 1;
        for (Page page : pages) {
            if (page.number() != expected++) {
                throw new IllegalArgumentException("학습 자료의 페이지 순서가 올바르지 않습니다.");
            }
        }

        if (pages.stream().allMatch(page -> page.text().isBlank())) {
            throw new IllegalArgumentException("학습 자료에서 읽을 수 있는 텍스트가 없습니다.");
        }
    }

    public void validatePlan(StudyPlan plan) {
        for (StudyPlan.Chapter chapter : plan.chapters()) {
            for (Integer page : chapter.pages()) {
                if (page == null || page < 1 || page > pages.size() || pages.get(page - 1).text().isBlank()) {
                    throw new IllegalArgumentException("강의 계획이 참조하는 원문 페이지가 없거나 비어 있습니다.");
                }
            }
        }
    }

    public String text() {
        return pages.stream().map(page -> "[page " + page.number() + "]\n" + page.text())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    public record Page(int number, String text) {
        public Page {
            if (number <= 0 || text == null) {
                throw new IllegalArgumentException("페이지 번호 또는 텍스트 정보가 올바르지 않습니다.");
            }
        }
    }
}
