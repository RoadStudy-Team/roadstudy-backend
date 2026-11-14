package com.roadstudy.application.study.port.in;

import com.roadstudy.application.study.model.StudyAudio;

public interface FindStudyAudio {
    StudyAudio find(String audioId);
}
