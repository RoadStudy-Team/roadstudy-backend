package com.roadstudy.application.study;

import java.util.UUID;

import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.port.in.FindStudyAudio;
import com.roadstudy.application.study.port.out.AudioStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyQueryService implements FindStudyAudio {
    private final AudioStorage audioStorage;

    @Override
    public StudyAudio find(String audioId) {
        UUID id = parseAudioId(audioId);
        return audioStorage.find(id).orElseThrow(StudyAudioException::notFound);
    }

    private static UUID parseAudioId(String audioId) {
        if (audioId == null) {
            throw StudyAudioException.notFound();
        }
        UUID id;
        try {
            id = UUID.fromString(audioId);
        } catch (IllegalArgumentException ex) {
            throw StudyAudioException.notFound();
        }

        if (!id.toString().equalsIgnoreCase(audioId)) {
            throw StudyAudioException.notFound();
        }

        return id;
    }
}
