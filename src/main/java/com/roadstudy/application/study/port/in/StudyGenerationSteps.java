package com.roadstudy.application.study.port.in;

import java.io.InputStream;

import com.roadstudy.application.study.ExtractedStudyText;
import com.roadstudy.application.study.StudyAudio;
import com.roadstudy.domain.study.StudySession;

public interface StudyGenerationSteps {
    ExtractedStudyText extractText(InputStream input);

    StudySession generateExplanation(String originalFileName, InputStream input);

    StudyAudio generateAudio(String explanationScript);
}
