package com.roadstudy.adapter.persistence;

import com.roadstudy.domain.study.StudyChapter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.util.*;

interface StudyChapterJpaRepository extends JpaRepository<StudyChapter, Long> {

    List<StudyChapter> findAllBySessionIdOrderByChapterOrder(Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StudyChapter c where c.id = :id")
    Optional<StudyChapter> lockById(Long id);
}
