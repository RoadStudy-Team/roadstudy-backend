package com.roadstudy.domain.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class InvalidStudyAudioException extends ApplicationException {
    public InvalidStudyAudioException(String field) {
        super("INVALID_STUDY_AUDIO", field + " 값이 올바르지 않습니다.", FailureType.INVALID_INPUT);
    }
}
