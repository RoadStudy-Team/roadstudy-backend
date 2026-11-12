package com.roadstudy.adapter.web.response;

import java.util.UUID;

import com.roadstudy.application.study.StudyAudio;

public record StudyAudioResponse(UUID audioId, String audioUrl) {

    public static StudyAudioResponse from(StudyAudio audio) {
        return new StudyAudioResponse(audio.audioId(), "/api/studies/audio/" + audio.audioId());
    }
}
