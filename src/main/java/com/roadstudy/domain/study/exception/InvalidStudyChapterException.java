package com.roadstudy.domain.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class InvalidStudyChapterException extends ApplicationException {
    public InvalidStudyChapterException(String field) {
        super("INVALID_STUDY_CHAPTER", field + " 값이 올바르지 않습니다.", FailureType.INVALID_INPUT);
    }
}
