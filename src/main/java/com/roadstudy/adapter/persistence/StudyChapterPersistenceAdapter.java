package com.roadstudy.adapter.persistence;

import com.roadstudy.application.study.port.out.StudyChapterRepository;
import com.roadstudy.domain.study.StudyChapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudyChapterPersistenceAdapter implements StudyChapterRepository {
    private final StudyChapterJpaRepository studyChapterRepository;

    @Override
    public Optional<StudyChapter> findById(Long id) {
        return studyChapterRepository.findById(id);
    }

    @Override
    public List<StudyChapter> findAllBySessionId(Long sessionId) {
        return studyChapterRepository.findAllBySessionIdOrderByChapterOrder(sessionId);
    }
}
