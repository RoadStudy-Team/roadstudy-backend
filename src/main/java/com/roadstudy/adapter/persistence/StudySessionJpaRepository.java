package com.roadstudy.adapter.persistence;

import com.roadstudy.domain.study.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface StudySessionJpaRepository extends JpaRepository<StudySession, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudySession s where s.id = :id")
    Optional<StudySession> lockById(Long id);

    List<StudySession> findAllByGenerationActiveTrue();
}
