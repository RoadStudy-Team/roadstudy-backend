package com.roadstudy.application.study.port.in;

import java.io.InputStream;

import com.roadstudy.domain.study.StudySession;

public interface StartStudyGeneration {
    StudySession start(String originalFileName, InputStream input);
}
