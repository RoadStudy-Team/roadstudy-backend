package com.roadstudy.application.study.port.in;

import com.roadstudy.application.study.StudyAudio;

public interface FindStudyAudio {
    StudyAudio find(String audioId);
}
