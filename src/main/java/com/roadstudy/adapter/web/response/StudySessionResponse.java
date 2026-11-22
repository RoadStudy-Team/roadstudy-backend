package com.roadstudy.adapter.web.response;

import com.roadstudy.application.study.model.StudySessionView;

import java.util.List;

public record StudySessionResponse(Long id, String originalFileName, String title, String summary,
                                  String status, boolean generationActive, String failureCode,
                                  List<String> notices, List<ChapterResponse> chapters) {

    public static StudySessionResponse from(StudySessionView view) {
        return new StudySessionResponse(view.id(), view.originalFileName(), view.title(), view.summary(),
                view.status(), view.generationActive(), view.failureCode(), view.notices(),
                view.chapters().stream().map(ChapterResponse::from).toList());
    }
}
