package com.roadstudy.adapter.web.response;

import com.roadstudy.application.study.model.StudySessionView;

import java.util.List;

public record ChapterResponse(Long id, int order, String title, String objective,
                              String sourceReferences, List<AudioResponse> audios) {

    public static ChapterResponse from(StudySessionView.Chapter chapter) {
        return new ChapterResponse(chapter.id(), chapter.order(), chapter.title(), chapter.objective(),
                chapter.sourceReferences(), chapter.audios().stream().map(AudioResponse::from).toList());
    }
}
