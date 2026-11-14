package com.roadstudy.application.study.port.in;

import com.roadstudy.domain.study.StudySession;

import java.io.InputStream;

public interface StartStudyGeneration {
    StudySession start(String originalFileName, InputStream input);
}
