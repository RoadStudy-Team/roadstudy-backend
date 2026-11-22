package com.roadstudy.domain.study.request;

import com.roadstudy.domain.study.enums.AudioVariant;

public record StudyAudioCreateRequest(Long chapterId, AudioVariant variant) {
}
