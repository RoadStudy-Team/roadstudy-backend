package com.roadstudy.application.study.port.out;

import com.roadstudy.domain.study.StudySession;

import java.util.List;
import java.util.Optional;

public interface StudyRepository {
    StudySession save(StudySession study);

    Optional<StudySession> findById(Long id);

    List<StudySession> findAllByCreatedAtDescending();
}
