package com.roadstudy.application.study.port.in;

import java.util.List;

import com.roadstudy.domain.study.StudySession;

public interface FindStudy {
    StudySession findById(Long studyId);

    List<StudySession> findAll();
}
