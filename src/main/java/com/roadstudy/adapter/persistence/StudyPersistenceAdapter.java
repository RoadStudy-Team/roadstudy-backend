package com.roadstudy.adapter.persistence;

import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.domain.study.StudySession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudyPersistenceAdapter implements StudyRepository {
    private final StudyJpaRepository repository;

    @Override
    public List<StudySession> findAllByCreatedAtDescending() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Override
    public StudySession save(StudySession study) {
        return repository.save(study);
    }

    @Override
    public Optional<StudySession> findById(Long id) {
        return repository.findById(id);
    }
}
