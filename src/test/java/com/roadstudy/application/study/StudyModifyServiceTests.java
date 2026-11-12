package com.roadstudy.application.study;

import java.io.ByteArrayInputStream;

import com.roadstudy.application.study.exception.StudyExplanationException;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.global.exception.FailureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("학습 대본 생성 입력 길이 검증")
class StudyModifyServiceTests {
    private static final byte[] DOCUMENT = {1};

    private final DocumentReader reader = mock(DocumentReader.class);
    private final StudyGenerator generator = mock(StudyGenerator.class);
    private final StudyModifyService service = new StudyModifyService(reader, generator);

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    @DisplayName("앞뒤 공백을 제외하고 유니코드 문자 6만 자를 허용한다")
    void acceptsExactLimit(String character) throws Exception {
        String text = character.repeat(60_000);
        var explanation = new StudyExplanation("제목", "요약", "대본");
        when(reader.read(DOCUMENT)).thenReturn(new ExtractedStudyText(1, " " + text + "\n"));
        when(generator.generate(text)).thenReturn(explanation);

        assertThat(service.generate(new ByteArrayInputStream(DOCUMENT))).isEqualTo(explanation);
    }

    @Test
    @DisplayName("6만 자보다 한 자 길면 생성기 호출 전에 거부한다")
    void rejectsOneCharacterOverLimit() {
        when(reader.read(DOCUMENT)).thenReturn(new ExtractedStudyText(1, "가".repeat(60_001)));

        assertThatExceptionOfType(StudyExplanationException.class)
                .isThrownBy(() -> service.generate(new ByteArrayInputStream(DOCUMENT)))
                .satisfies(ex -> {
                    assertThat(ex.getFailureType()).isEqualTo(FailureType.UNPROCESSABLE_CONTENT);
                    assertThat(ex.getCode()).isEqualTo("STUDY_TEXT_TOO_LONG");
                });
        verifyNoInteractions(generator);
    }
}
