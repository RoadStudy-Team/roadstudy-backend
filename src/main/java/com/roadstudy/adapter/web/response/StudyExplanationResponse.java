package com.roadstudy.adapter.web.response;

import com.roadstudy.application.study.StudyExplanation;

public record StudyExplanationResponse(String title, String summary, String explanationScript) {

    public static StudyExplanationResponse from(StudyExplanation explanation) {
        return new StudyExplanationResponse(explanation.title(), explanation.summary(), explanation.explanationScript());
    }
}
