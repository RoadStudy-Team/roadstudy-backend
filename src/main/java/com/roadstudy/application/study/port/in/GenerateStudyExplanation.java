package com.roadstudy.application.study.port.in;

import java.io.IOException;
import java.io.InputStream;

import com.roadstudy.application.study.StudyExplanation;

public interface GenerateStudyExplanation {
    StudyExplanation generate(InputStream input) throws IOException;
}
