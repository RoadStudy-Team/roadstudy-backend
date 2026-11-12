package com.roadstudy.adapter.web.response;

import java.nio.file.Path;

import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudyStatus;

public record StudyResponse(Long id, String originalFileName, StudyStatus status, String title, String summary,
                            String explanationScript, String audioUrl) {

    public static StudyResponse from(StudySession session) {
        return new StudyResponse(session.getId(), session.getOriginalFileName(), session.getStatus(),
                session.getTitle(), session.getSummary(), session.getExplanationScript(),
                audioUrlOf(session.getAudioPath()));
    }

    // 세션에는 로컬 MP3 경로만 기록되므로 파일명에 담긴 음성 ID로 조회 주소를 만든다.
    private static String audioUrlOf(String audioPath) {
        if (audioPath == null) {
            return null;
        }

        String fileName = Path.of(audioPath).getFileName().toString();
        int extensionStart = fileName.lastIndexOf('.');
        String audioId = extensionStart < 0 ? fileName : fileName.substring(0, extensionStart);
        return "/api/studies/audio/" + audioId;
    }
}
