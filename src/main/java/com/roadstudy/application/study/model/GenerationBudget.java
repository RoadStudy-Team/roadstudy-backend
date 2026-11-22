package com.roadstudy.application.study.model;

import com.roadstudy.application.study.exception.StudyGenerationException;

public final class GenerationBudget {
    private final long tokenLimit;
    private final int callLimit;
    private long tokens;
    private int calls;
    private long reservation;

    public GenerationBudget(long tokenLimit, int callLimit) {
        if (tokenLimit <= 0 || callLimit <= 0) {
            throw new IllegalArgumentException("학습 생성의 토큰 또는 호출 한도가 올바르지 않습니다.");
        }
        this.tokenLimit = tokenLimit;
        this.callLimit = callLimit;
    }

    public void reserve(int estimatedInput, int maxOutput) {
        if (estimatedInput < 0 || maxOutput <= 0) {
            throw new IllegalArgumentException("예약할 입력 또는 출력 토큰 수가 올바르지 않습니다.");
        }
        if (calls >= callLimit || tokens + estimatedInput + maxOutput > tokenLimit) {
            throw StudyGenerationException.budgetExceeded();
        }
        reservation = (long) estimatedInput + maxOutput;
        tokens += reservation;
        calls++;
    }

    public void settle(long input, long output) {
        if (input < 0 || output < 0) {
            throw new IllegalArgumentException("실제 입력 또는 출력 토큰 사용량은 음수일 수 없습니다.");
        }
        tokens = tokens - reservation + input + output;
        reservation = 0;
    }

    public long tokens() {
        return tokens;
    }

    public int calls() {
        return calls;
    }
}
