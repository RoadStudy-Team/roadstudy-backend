package com.roadstudy.application.study;

import java.util.List;
import java.util.UUID;

import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.exception.StudyNotFoundException;
import com.roadstudy.application.study.port.in.FindStudy;
import com.roadstudy.application.study.port.in.FindStudyAudio;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.domain.study.StudySession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyQueryService implements FindStudyAudio, FindStudy {
    private final AudioStorage audioStorage;
    private final StudyRepository studyRepository;

    @Override
    public StudyAudio find(String audioId) {
        UUID id = parseAudioId(audioId);
        return audioStorage.find(id).orElseThrow(StudyAudioException::notFound);
    }

    @Override
    public StudySession findById(Long studyId) {
        return studyRepository.findById(studyId).orElseThrow(StudyNotFoundException::new);
    }

    @Override
    public List<StudySession> findAll() {
        return studyRepository.findAllByCreatedAtDescending();
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
