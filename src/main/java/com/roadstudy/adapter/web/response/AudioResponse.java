package com.roadstudy.adapter.web.response;

import com.roadstudy.application.study.model.StudySessionView;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.enums.GenerationStage;

public record AudioResponse(Long id, AudioVariant variant, AudioStatus status, GenerationStage stage,
                            String script, String failureCode, String audioUrl) {

    public static AudioResponse from(StudySessionView.Audio audio) {
        return new AudioResponse(audio.id(), audio.variant(), audio.status(), audio.stage(), audio.script(),
                audio.failureCode(), audio.status() == AudioStatus.READY ? "/api/studies/audio/" + audio.id() : null);
    }
}
