package com.roadstudy.application.study.port.out;

import com.roadstudy.application.study.model.StoredAudio;

import java.util.Optional;
import java.util.UUID;

public interface AudioStorage {
    StoredAudio save(UUID audioId, byte[] content);

    Optional<StoredAudio> find(UUID audioId);
}
