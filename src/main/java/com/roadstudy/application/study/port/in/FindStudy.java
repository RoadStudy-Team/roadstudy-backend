package com.roadstudy.application.study.port.in;

import com.roadstudy.domain.study.StudySession;

public interface FindStudy {
    StudySession findById(Long studyId);
}
