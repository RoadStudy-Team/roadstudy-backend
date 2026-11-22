package com.roadstudy.adapter.persistence;

import com.roadstudy.domain.study.request.StudyAudioCreateRequest;
import com.roadstudy.application.study.port.out.StudySessionRepository;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.StudyAudio;
import com.roadstudy.domain.study.StudyChapter;
import com.roadstudy.domain.study.StudyPlan;
import com.roadstudy.domain.study.StudySession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudySessionPersistenceAdapter implements StudySessionRepository {
    private final StudySessionJpaRepository studySessionRepository;
    private final StudyChapterJpaRepository studyChapterRepository;
    private final StudyAudioJpaRepository studyAudioRepository;

    @Override
    public StudySession save(StudySession session) {
        return studySessionRepository.save(session);
    }

    @Override
    public Optional<StudySession> findById(Long id) {
        return studySessionRepository.findById(id);
    }

    @Override
    public List<StudySession> findAllByCreatedAtDescending() {
        return studySessionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Override
    @Transactional
    public boolean claimGeneration(Long id) {
        StudySession session = studySessionRepository.lockById(id).orElseThrow();
        return session.claimGeneration();
    }

    @Override
    @Transactional
    public void releaseGeneration(Long id) {
        StudySession session = studySessionRepository.lockById(id).orElseThrow();
        session.releaseGeneration();
    }

    @Override
    @Transactional
    public void savePlan(Long id, StudyPlan plan, List<StudyChapter> chapters, AudioVariant initialVariant) {
        StudySession session = studySessionRepository.lockById(id).orElseThrow();
        if (session.getPlan() != null) {
            return;
        }
        // 계획만 저장되고 챕터나 기본 음성이 누락되지 않도록 함께 저장
        session.changePlan(plan);
        for (StudyChapter chapter : chapters) {
            StudyChapter savedChapter = studyChapterRepository.save(chapter);
            StudyAudioCreateRequest request = new StudyAudioCreateRequest(savedChapter.getId(), initialVariant);
            StudyAudio audio = StudyAudio.create(request);
            studyAudioRepository.save(audio);
        }
    }

    @Override
    @Transactional
    public void interruptRunning() {
        List<StudySession> sessions = studySessionRepository.findAllByGenerationActiveTrue();
        for (StudySession session : sessions) {
            session.interruptGeneration();
        }
    }
}
