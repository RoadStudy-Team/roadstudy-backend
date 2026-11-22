package com.roadstudy.application.study.model;

public record StudyGenerationSettings(long tokenBudget, int callLimit, long retryDelayMillis) {
    public StudyGenerationSettings {
        if (tokenBudget <= 0 || callLimit <= 0 || retryDelayMillis < 0) {
            throw new IllegalArgumentException("학습 생성 설정값이 올바르지 않습니다.");
        }
    }
}
