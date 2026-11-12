package com.roadstudy.application.study;

import java.io.ByteArrayInputStream;

import com.roadstudy.application.study.exception.StudyExplanationException;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudyStatus;
import com.roadstudy.global.exception.FailureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("학습 대본 생성 입력 길이 검증")
class StudyModifyServiceTests {
    private static final byte[] DOCUMENT = {1};

    private final DocumentReader reader = mock(DocumentReader.class);
    private final StudyGenerator generator = mock(StudyGenerator.class);
    private final StudyRepository repository = mock(StudyRepository.class);
    private final StudyModifyService service = new StudyModifyService(
            reader, generator, repository, mock(SpeechSynthesizer.class), mock(AudioStorage.class), Runnable::run);

    @Test
    @DisplayName("앞뒤 공백을 제외하고 유니코드 문자 6만 자를 허용한다")
    void acceptsExactLimit() throws Exception {
        String text = "😀".repeat(60_000);
        var explanation = new StudyExplanation("제목", "요약", "대본");
        when(reader.read(DOCUMENT)).thenReturn(new ExtractedStudyText(1, " " + text + "\n"));
        when(generator.generate(text)).thenReturn(explanation);

        when(repository.save(any(StudySession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudySession session = service.generate("lecture.pdf", new ByteArrayInputStream(DOCUMENT));

        assertThat(session.getOriginalFileName()).isEqualTo("lecture.pdf");
        assertThat(session.getTitle()).isEqualTo(explanation.title());
        assertThat(session.getSummary()).isEqualTo(explanation.summary());
        assertThat(session.getExplanationScript()).isEqualTo(explanation.explanationScript());
        assertThat(session.getStatus()).isEqualTo(StudyStatus.PROCESSING);
        verify(repository).save(session);
    }

    @Test
    @DisplayName("6만 자보다 한 자 길면 생성기 호출 전에 거부한다")
    void rejectsOneCharacterOverLimit() {
        when(reader.read(DOCUMENT)).thenReturn(new ExtractedStudyText(1, "가".repeat(60_001)));

        assertThatExceptionOfType(StudyExplanationException.class)
                .isThrownBy(() -> service.generate("lecture.pdf", new ByteArrayInputStream(DOCUMENT)))
                .satisfies(ex -> {
                    assertThat(ex.getFailureType()).isEqualTo(FailureType.UNPROCESSABLE_CONTENT);
                    assertThat(ex.getCode()).isEqualTo("STUDY_TEXT_TOO_LONG");
                });
        verifyNoInteractions(generator, repository);
    }
}
