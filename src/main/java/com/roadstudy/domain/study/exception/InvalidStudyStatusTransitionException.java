package com.roadstudy.domain.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class InvalidStudyStatusTransitionException extends ApplicationException {
    public InvalidStudyStatusTransitionException() {
        super("INVALID_STUDY_STATUS_TRANSITION",
                "처리 중인 학습 세션만 변경할 수 있습니다.",
                FailureType.CONFLICT
        );
    }
}
