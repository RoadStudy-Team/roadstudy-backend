package com.roadstudy.adapter.web.response;

import com.roadstudy.domain.study.StudySession;

public record StudyExplanationResponse(Long id, String title, String summary, String explanationScript) {

    public static StudyExplanationResponse from(StudySession session) {
        return new StudyExplanationResponse(session.getId(), session.getTitle(), session.getSummary(), session.getExplanationScript());
    }
}
