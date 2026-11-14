package com.roadstudy.application.study.port.in;

import com.roadstudy.domain.study.StudySession;

import java.util.List;

public interface FindStudy {
    StudySession findById(Long studyId);

    List<StudySession> findAll();
}
