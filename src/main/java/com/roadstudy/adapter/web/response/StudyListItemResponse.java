package com.roadstudy.adapter.web.response;

import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudyStatus;

import java.time.LocalDateTime;

public record StudyListItemResponse(
        Long id, String originalFileName, String title, String summary, StudyStatus status, LocalDateTime createdAt
) {
    public static StudyListItemResponse from(StudySession session) {
        return new StudyListItemResponse(session.getId(), session.getOriginalFileName(), session.getTitle(),
                session.getSummary(), session.getStatus(), session.getCreatedAt());
    }
}
