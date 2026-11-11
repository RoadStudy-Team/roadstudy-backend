package com.roadstudy.application.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class StudyAudioException extends ApplicationException {
    private StudyAudioException(String code, String message, FailureType type) {
        this(code, message, type, null);
    }

    private StudyAudioException(String code, String message, FailureType type, Throwable cause) {
        super(code, message, type, cause);
    }

    public static StudyAudioException emptyScript() {
        return new StudyAudioException("EMPTY_SCRIPT", "음성으로 변환할 설명 대본을 입력해 주세요.", FailureType.INVALID_INPUT);
    }

    public static StudyAudioException scriptTooLong(int limit) {
        return new StudyAudioException("TTS_INPUT_TOO_LONG",
                "설명 대본이 음성 합성 최대 크기 " + limit + "바이트를 초과했습니다.", FailureType.UNPROCESSABLE_CONTENT);
    }

    public static StudyAudioException generationFailed() {
        return generationFailed(null);
    }

    public static StudyAudioException generationFailed(Throwable cause) {
        return new StudyAudioException("TTS_GENERATION_FAILED",
                "음성 합성 서비스 호출에 실패했습니다.", FailureType.EXTERNAL_SERVICE_FAILURE, cause);
    }

    public static StudyAudioException generationTimedOut() {
        return generationTimedOut(null);
    }

    public static StudyAudioException generationTimedOut(Throwable cause) {
        return new StudyAudioException("TTS_TIMEOUT",
                "음성 합성 서비스의 응답 시간이 초과되었습니다.", FailureType.EXTERNAL_SERVICE_TIMEOUT, cause);
    }

    public static StudyAudioException notFound() {
        return new StudyAudioException("AUDIO_NOT_FOUND", "요청한 음성 파일을 찾을 수 없습니다.", FailureType.NOT_FOUND);
    }

    public static StudyAudioException storageFailed() {
        return storageFailed(null);
    }

    public static StudyAudioException storageFailed(Throwable cause) {
        return new StudyAudioException("AUDIO_STORAGE_FAILED",
                "생성한 음성 파일을 저장하지 못했습니다.", FailureType.INTERNAL_FAILURE, cause);
    }
}
