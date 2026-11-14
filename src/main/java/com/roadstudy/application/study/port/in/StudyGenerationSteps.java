package com.roadstudy.application.study.port.in;

import com.roadstudy.application.study.model.ExtractedStudyText;
import com.roadstudy.application.study.model.StudyAudio;
import com.roadstudy.domain.study.StudySession;

import java.io.InputStream;

public interface StudyGenerationSteps {
    ExtractedStudyText extractText(InputStream input);

    StudySession generateExplanation(String originalFileName, InputStream input);

    StudyAudio generateAudio(String explanationScript);
}
