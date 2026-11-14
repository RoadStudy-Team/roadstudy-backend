package com.roadstudy.application.study.port.in;

import com.roadstudy.domain.study.StudySession;

import java.io.InputStream;

public interface GenerateStudy {
    StudySession generateStudyAsync(String originalFileName, InputStream input);
}
