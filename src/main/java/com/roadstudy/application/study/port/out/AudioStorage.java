package com.roadstudy.application.study.port.out;

import java.util.Optional;
import java.util.UUID;

import com.roadstudy.application.study.StudyAudio;

public interface AudioStorage {
    StudyAudio save(UUID audioId, byte[] content);

    Optional<StudyAudio> find(UUID audioId);
}
