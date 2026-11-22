package com.roadstudy.application.study.port.out;

import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.StudyChapter;
import com.roadstudy.domain.study.StudyPlan;
import com.roadstudy.domain.study.StudySession;

import java.util.List;
import java.util.Optional;

public interface StudySessionRepository {
    StudySession save(StudySession session);

    Optional<StudySession> findById(Long id);

    List<StudySession> findAllByCreatedAtDescending();

    boolean claimGeneration(Long id);

    void releaseGeneration(Long id);

    void savePlan(Long id, StudyPlan plan, List<StudyChapter> chapters, AudioVariant initialVariant);

    void interruptRunning();
}
