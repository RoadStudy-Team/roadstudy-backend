package com.roadstudy.domain.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class InvalidStudyStatusTransitionException extends ApplicationException {
    public InvalidStudyStatusTransitionException() {
        super("INVALID_STUDY_STATUS_TRANSITION", "현재 상태에서는 학습 정보를 변경할 수 없습니다.", FailureType.CONFLICT);
    }
}
