package com.roadstudy.application.study.port.out;

import com.roadstudy.domain.study.StudyChapter;

import java.util.List;
import java.util.Optional;

public interface StudyChapterRepository {
    Optional<StudyChapter> findById(Long id);

    List<StudyChapter> findAllBySessionId(Long sessionId);
}
