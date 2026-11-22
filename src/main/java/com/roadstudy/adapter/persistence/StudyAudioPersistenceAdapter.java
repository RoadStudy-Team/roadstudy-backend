package com.roadstudy.adapter.persistence;

import com.roadstudy.domain.study.request.StudyAudioCreateRequest;
import com.roadstudy.application.study.port.out.StudyAudioRepository;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.StudyAudio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudyAudioPersistenceAdapter implements StudyAudioRepository {
    private final StudyAudioJpaRepository studyAudioRepository;
    private final StudyChapterJpaRepository studyChapterRepository;

    @Override
    public StudyAudio save(StudyAudio audio) {
        return studyAudioRepository.saveAndFlush(audio);
    }

    @Override
    public Optional<StudyAudio> findById(Long id) {
        return studyAudioRepository.findById(id);
    }

    @Override
    public List<StudyAudio> findAllByChapterId(Long chapterId) {
        return studyAudioRepository.findAllByChapterId(chapterId);
    }

    @Override
    @Transactional
    public StudyAudio findOrCreate(Long chapterId, AudioVariant variant) {
        // 동일 챕터의 같은 버전이 동시에 생성되지 않도록 챕터 잠금
        studyChapterRepository.lockById(chapterId).orElseThrow();

        Optional<StudyAudio> existingAudio = studyAudioRepository.findByChapterIdAndVariant(chapterId, variant);
        if (existingAudio.isPresent()) {
            return existingAudio.get();
        }

        StudyAudioCreateRequest audioRequest = new StudyAudioCreateRequest(chapterId, variant);
        StudyAudio audio = StudyAudio.create(audioRequest);
        return studyAudioRepository.save(audio);
    }

    @Override
    @Transactional
    public Optional<StudyAudio> claimGeneration(Long id) {
        StudyAudio audio = studyAudioRepository.lockById(id).orElseThrow();
        if (!audio.tryBegin()) {
            return Optional.empty();
        }

        StudyAudio savedAudio = studyAudioRepository.saveAndFlush(audio);
        return Optional.of(savedAudio);
    }

    @Override
    @Transactional
    public void interruptRunning() {
        List<StudyAudio> audios = studyAudioRepository.findAllByStatus(AudioStatus.GENERATING);
        for (StudyAudio audio : audios) {
            audio.interrupt();
        }
    }
}
