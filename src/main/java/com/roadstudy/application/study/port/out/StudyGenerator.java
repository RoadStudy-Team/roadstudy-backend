package com.roadstudy.application.study.port.out;

import com.roadstudy.application.study.model.StudyExplanation;

public interface StudyGenerator {
    StudyExplanation generateExplanation(String text);
}
