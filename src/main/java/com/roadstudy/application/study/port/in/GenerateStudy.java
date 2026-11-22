package com.roadstudy.application.study.port.in;

import com.roadstudy.domain.study.StudyAudio;
import com.roadstudy.domain.study.StudySession;

import java.io.InputStream;

public interface GenerateStudy {
    StudySession generateStudyAsync(String originalFileName, InputStream input);

    void retryGeneration(Long sessionId);

    StudyAudio generateDetailedAudioAsync(Long sessionId, Long chapterId);

    void retryAudioGeneration(Long sessionId, Long chapterId, Long audioId);

    void interruptGeneration();
}
