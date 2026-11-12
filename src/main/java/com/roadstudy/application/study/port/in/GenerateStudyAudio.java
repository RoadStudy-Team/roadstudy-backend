package com.roadstudy.application.study.port.in;

import com.roadstudy.application.study.StudyAudio;

public interface GenerateStudyAudio {
    StudyAudio generate(String explanationScript);
}
