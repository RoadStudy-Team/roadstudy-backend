package com.roadstudy.application.study.port.out;

import com.roadstudy.application.study.StudyExplanation;

public interface StudyGenerator {
    StudyExplanation generate(String text);
}
