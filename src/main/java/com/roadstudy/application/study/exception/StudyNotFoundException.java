package com.roadstudy.application.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class StudyNotFoundException extends ApplicationException {
    public StudyNotFoundException() {
        super("STUDY_NOT_FOUND", "요청한 학습 세션을 찾을 수 없습니다.", FailureType.NOT_FOUND);
    }
}
