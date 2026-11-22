package com.roadstudy.application.study.port.out;

import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.StudyAudio;

import java.util.List;
import java.util.Optional;

public interface StudyAudioRepository {
    StudyAudio save(StudyAudio audio);

    Optional<StudyAudio> findById(Long id);

    List<StudyAudio> findAllByChapterId(Long chapterId);

    StudyAudio findOrCreate(Long chapterId, AudioVariant variant);

    Optional<StudyAudio> claimGeneration(Long id);

    void interruptRunning();
}
