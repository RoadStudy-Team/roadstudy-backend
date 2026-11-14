package com.roadstudy.adapter.web.response;

import com.roadstudy.application.study.model.StudyAudio;

import java.util.UUID;

public record StudyAudioResponse(UUID audioId, String audioUrl) {

    public static StudyAudioResponse from(StudyAudio audio) {
        return new StudyAudioResponse(audio.audioId(), "/api/studies/audio/" + audio.audioId());
    }
}
