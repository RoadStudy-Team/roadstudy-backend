package com.roadstudy.application.study.port.in;

import com.roadstudy.application.study.model.StudySessionView;

import java.nio.file.Path;
import java.util.List;

public interface FindStudy {
    StudySessionView findById(Long sessionId);

    List<StudySessionView> findAll();

    Path findAudio(Long audioId);
}
