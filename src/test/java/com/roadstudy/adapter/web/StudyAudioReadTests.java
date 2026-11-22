package com.roadstudy.adapter.web;

import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.port.in.FindStudy;
import com.roadstudy.application.study.port.in.GenerateStudy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("음성 재생 파일 읽기 오류")
class StudyAudioReadTests {
    @TempDir
    Path directory;

    @Test
    @DisplayName("구간 재생 검증 중 파일을 읽지 못하면 전용 예외로 변환한다")
    void translatesFileReadFailureDuringRangeValidation() {
        FindStudy findStudy = mock(FindStudy.class);
        GenerateStudy generateStudy = mock(GenerateStudy.class);
        when(findStudy.findAudio(1L)).thenReturn(directory.resolve("missing.mp3"));
        StudyController controller = new StudyController(generateStudy, findStudy);

        assertThatThrownBy(() -> controller.getAudio(1L, "bytes=0-1"))
                .isInstanceOfSatisfying(StudyAudioException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("AUDIO_READ_FAILED");
                    assertThat(failure.getCause()).isInstanceOf(IOException.class);
                });
    }
}
