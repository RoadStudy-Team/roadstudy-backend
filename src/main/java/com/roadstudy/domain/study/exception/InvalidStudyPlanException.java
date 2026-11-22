package com.roadstudy.domain.study.exception;

import com.roadstudy.domain.study.StudyPlan;
import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class InvalidStudyPlanException extends ApplicationException {
    public InvalidStudyPlanException(String field) {
        super("INVALID_STUDY_PLAN", field + " 값이 올바르지 않습니다.", FailureType.INVALID_INPUT);
    }
}
