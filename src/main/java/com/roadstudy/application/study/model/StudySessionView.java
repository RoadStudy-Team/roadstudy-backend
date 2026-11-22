package com.roadstudy.application.study.model;

import com.roadstudy.domain.study.enums.GenerationStage;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.domain.study.*;

import java.util.List;

public record StudySessionView(Long id, String originalFileName, String title, String summary,
        String status, boolean generationActive, String failureCode, List<String> notices, List<Chapter> chapters) {

    public record Chapter(Long id, int order, String title, String objective, String sourceReferences, List<Audio> audios) {}

    public record Audio(Long id, AudioVariant variant, AudioStatus status, GenerationStage stage, String script, String failureCode) {}
}
