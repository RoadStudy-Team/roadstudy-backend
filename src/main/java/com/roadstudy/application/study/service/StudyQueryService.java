package com.roadstudy.application.study.service;

import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.exception.StudyNotFoundException;
import com.roadstudy.application.study.model.StoredAudio;
import com.roadstudy.application.study.model.StudySessionView;
import com.roadstudy.application.study.port.in.FindStudy;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.StudyChapterRepository;
import com.roadstudy.application.study.port.out.StudyAudioRepository;
import com.roadstudy.application.study.port.out.StudySessionRepository;
import com.roadstudy.domain.study.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyQueryService implements FindStudy {
    private final StudySessionRepository studySessionRepository;
    private final StudyChapterRepository studyChapterRepository;
    private final StudyAudioRepository studyAudioRepository;
    private final AudioStorage audioStorage;

    @Override
    public List<StudySessionView> findAll() {
        return studySessionRepository.findAllByCreatedAtDescending().stream()
                .map(this::toStudyView)
                .toList();
    }

    @Override
    public StudySessionView findById(Long sessionId) {
        StudySession session = studySessionRepository.findById(sessionId).orElseThrow(StudyNotFoundException::new);
        return toStudyView(session);
    }

    @Override
    public Path findAudio(Long audioId) {
        StudyAudio audio = studyAudioRepository.findById(audioId).orElseThrow(StudyAudioException::notFound);
        if (audio.getStatus() != AudioStatus.READY || audio.getStorageKey() == null) {
            throw StudyAudioException.notFound();
        }
        try {
            UUID fileId = UUID.fromString(audio.getStorageKey());
            StoredAudio storedAudio = audioStorage.find(fileId).orElseThrow(StudyAudioException::notFound);
            return storedAudio.path();
        } catch (IllegalArgumentException ex) {
            throw StudyAudioException.notFound();
        }
    }

    private StudySessionView toStudyView(StudySession session) {
        List<StudySessionView.Chapter> chapters = studyChapterRepository.findAllBySessionId(session.getId()).stream()
                .map(this::toChapterView)
                .toList();

        List<StudySessionView.Audio> basicAudios = chapters.stream()
                .flatMap(chapter -> chapter.audios().stream())
                .filter(audio -> audio.variant() == AudioVariant.BASIC)
                .toList();

        List<String> notices = session.getPlan() == null ? List.of() : session.getPlan().notices();
        String status = studyStatus(session, basicAudios, chapters.size());

        return new StudySessionView(session.getId(), session.getOriginalFileName(), session.getTitle(), session.getSummary(),
                status, session.isGenerationActive(), session.getSessionFailureCode(), notices, chapters);
    }

    private StudySessionView.Chapter toChapterView(StudyChapter chapter) {
        List<StudySessionView.Audio> audios = studyAudioRepository.findAllByChapterId(chapter.getId()).stream()
                .map(this::toAudioView)
                .toList();

        return new StudySessionView.Chapter(chapter.getId(), chapter.getChapterOrder(), chapter.getTitle(),
                chapter.getObjective(), chapter.getSourceReferences(), audios);
    }

    private StudySessionView.Audio toAudioView(StudyAudio audio) {
        return new StudySessionView.Audio(audio.getId(), audio.getVariant(), audio.getStatus(),
                audio.getStage(), audio.getScript(), audio.getFailureCode());
    }

    private String studyStatus(StudySession session, List<StudySessionView.Audio> basicAudios, int chapterCount) {
        long readyCount = basicAudios.stream().filter(audio -> audio.status() == AudioStatus.READY).count();
        if (session.isGenerationActive()) {
            return readyCount > 0 ? "PARTIALLY_READY" : "PROCESSING";
        }
        if (readyCount > 0 && readyCount == basicAudios.size() && chapterCount == basicAudios.size()) {
            return "COMPLETED";
        }
        if (readyCount > 0) {
            return "PARTIALLY_READY";
        }
        if (session.isInterrupted() || basicAudios.stream().anyMatch(audio -> audio.status() == AudioStatus.INTERRUPTED)) {
            return "INTERRUPTED";
        }
        if (session.getSessionFailureCode() != null
                || basicAudios.stream().anyMatch(audio -> audio.status() == AudioStatus.FAILED)) {
            return "FAILED";
        }
        return "PENDING";
    }
}
