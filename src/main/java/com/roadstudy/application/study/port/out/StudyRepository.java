package com.roadstudy.application.study.port.out;

import java.util.List;
import java.util.Optional;

import com.roadstudy.domain.study.StudySession;

public interface StudyRepository {
    StudySession save(StudySession study);

    Optional<StudySession> findById(Long id);

    List<StudySession> findAllNewestFirst();
}
