package com.roadstudy.domain.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class InvalidStudySessionException extends ApplicationException {
    public InvalidStudySessionException(String field) {
        super("INVALID_STUDY_SESSION",
                field + " 값을 입력해야 합니다.",
                FailureType.INVALID_INPUT
        );
    }
}
