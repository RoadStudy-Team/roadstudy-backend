package com.roadstudy.application.study.port.out;

import com.roadstudy.application.study.model.StudyAudio;

import java.util.Optional;
import java.util.UUID;

public interface AudioStorage {
    StudyAudio save(UUID audioId, byte[] content);

    Optional<StudyAudio> find(UUID audioId);
}
