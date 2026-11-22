package com.roadstudy.domain.study.enums;

// 음성 생성 실패 시 이어서 재시도할 처리 단계 구분
public enum GenerationStage {
    SCRIPT,
    REVIEW,
    AUDIO,
    STORAGE,
    COMPLETE
}
