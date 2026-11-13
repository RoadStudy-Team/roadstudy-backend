package com.roadstudy.adapter.web.response;

import java.time.LocalDateTime;

import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudyStatus;

public record StudyListItemResponse(
        Long id, String originalFileName, String title, String summary, StudyStatus status, LocalDateTime createdAt
) {
    public static StudyListItemResponse from(StudySession session) {
        return new StudyListItemResponse(session.getId(), session.getOriginalFileName(), session.getTitle(),
                session.getSummary(), session.getStatus(), session.getCreatedAt());
    }
}
