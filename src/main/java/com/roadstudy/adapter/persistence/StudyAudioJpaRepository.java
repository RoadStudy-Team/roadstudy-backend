package com.roadstudy.adapter.persistence;

import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.domain.study.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.util.*;

interface StudyAudioJpaRepository extends JpaRepository<StudyAudio, Long> {

    List<StudyAudio> findAllByChapterId(Long chapterId);

    Optional<StudyAudio> findByChapterIdAndVariant(Long chapterId, AudioVariant variant);

    List<StudyAudio> findAllByStatus(AudioStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StudyAudio a where a.id = :id")
    Optional<StudyAudio> lockById(Long id);
}
