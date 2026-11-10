package com.roadstudy.application.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class StudyExplanationException extends ApplicationException {
    private StudyExplanationException(String code, String message, FailureType type) {
        super(code, message, type);
    }

    public static StudyExplanationException textTooLong(int limit) {
        return new StudyExplanationException("STUDY_TEXT_TOO_LONG",
                "추출한 학습 자료가 최대 길이 " + limit + "자를 초과했습니다.", FailureType.UNPROCESSABLE_CONTENT);
    }

    public static StudyExplanationException generationFailed() {
        return new StudyExplanationException("STUDY_GENERATION_FAILED",
                "학습 설명 생성 서비스 호출에 실패했습니다.", FailureType.EXTERNAL_SERVICE_FAILURE);
    }

    public static StudyExplanationException generationTimedOut() {
        return new StudyExplanationException("STUDY_GENERATION_TIMEOUT",
                "학습 설명 생성 서비스의 응답 시간이 초과되었습니다.", FailureType.EXTERNAL_SERVICE_TIMEOUT);
    }

    public static StudyExplanationException invalidResult() {
        return new StudyExplanationException("INVALID_STUDY_EXPLANATION",
                "학습 설명 생성 서비스에서 유효한 결과를 받지 못했습니다.", FailureType.EXTERNAL_SERVICE_FAILURE);
    }
}
