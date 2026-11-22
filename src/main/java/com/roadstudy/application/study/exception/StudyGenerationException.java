package com.roadstudy.application.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;
import lombok.Getter;

@Getter
public class StudyGenerationException extends ApplicationException {
    private final boolean retryable;

    private StudyGenerationException(String code, String message, FailureType type, boolean retryable) {
        super(code, message, type);
        this.retryable = retryable;
    }

    public static StudyGenerationException invalidPlan() {
        return failure("INVALID_STUDY_PLAN", "유효한 강의 계획을 생성하지 못했습니다.", false);
    }

    public static StudyGenerationException invalidResult() {
        return failure("INVALID_STUDY_RESULT", "유효한 학습 설명을 생성하지 못했습니다.", false);
    }

    public static StudyGenerationException externalServiceFailed(int status) {
        boolean retryable = status == 408 || status == 429 || status >= 500;
        return failure("OPENAI_HTTP_" + status, "학습 생성 서비스 호출에 실패했습니다.", retryable);
    }

    public static StudyGenerationException networkFailure() {
        return failure("OPENAI_NETWORK_FAILURE", "학습 생성 서비스에 연결하지 못했습니다.", true);
    }

    public static StudyGenerationException refused() {
        return failure("STUDY_REFUSED", "자료에 대한 학습 설명을 생성할 수 없습니다.", false);
    }

    public static StudyGenerationException sourceRequired() {
        return failure("SOURCE_REQUIRED", "학습 생성에 필요한 원본 자료가 없습니다.", false);
    }

    public static StudyGenerationException planRequired() {
        return failure("STUDY_PLAN_REQUIRED", "학습 생성에 필요한 강의 계획이 없습니다.", false);
    }

    public static StudyGenerationException invalidStoredPlan() {
        return new StudyGenerationException("INVALID_STORED_PLAN", "저장된 강의 계획을 읽지 못했습니다.",
                FailureType.INTERNAL_FAILURE, false);
    }

    public static StudyGenerationException interrupted() {
        return failure("GENERATION_INTERRUPTED", "학습 생성 작업이 중단되었습니다. 다시 시도해 주세요.", false);
    }

    public static StudyGenerationException budgetExceeded() {
        return failure("GENERATION_BUDGET_EXCEEDED", "생성 한도를 초과했습니다. PDF를 나누어 업로드해 주세요.", false);
    }

    private static StudyGenerationException failure(String code, String message, boolean retryable) {
        return new StudyGenerationException(code, message, FailureType.EXTERNAL_SERVICE_FAILURE, retryable);
    }
}
