package com.roadstudy.domain.study.request;

public record StudyChapterCreateRequest(Long sessionId, int order, String title,
                                        String objective, String references) {
}
