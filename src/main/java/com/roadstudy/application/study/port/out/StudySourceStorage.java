package com.roadstudy.application.study.port.out;

import com.roadstudy.application.study.model.StudyDocument;

import java.util.UUID;

public interface StudySourceStorage {
    String save(UUID id, byte[] pdf);

    StudyDocument read(String key);

    void delete(String key);
}
