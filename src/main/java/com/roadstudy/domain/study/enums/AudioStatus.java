package com.roadstudy.domain.study.enums;

// 음성 생성 작업의 진행 및 결과 상태 구분
public enum AudioStatus {
    PENDING,
    GENERATING,
    READY,
    FAILED,
    INTERRUPTED
}
