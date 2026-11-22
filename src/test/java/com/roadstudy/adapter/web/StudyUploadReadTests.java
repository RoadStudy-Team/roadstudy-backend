package com.roadstudy.adapter.web;

import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.model.StudySessionView;
import com.roadstudy.application.study.port.in.FindStudy;
import com.roadstudy.application.study.port.in.GenerateStudy;
import com.roadstudy.domain.study.StudySession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("PDF 업로드 스트림 오류 변환")
class StudyUploadReadTests {
    private final GenerateStudy generateStudy = mock(GenerateStudy.class);
    private final FindStudy findStudy = mock(FindStudy.class);
    private final StudyController controller = new StudyController(generateStudy, findStudy);

    @Test
    @DisplayName("스트림 열기 실패 시 학습 생성을 호출하지 않고 전용 예외로 변환한다")
    void translatesStreamOpenFailure() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        IOException cause = new IOException("스트림 열기 실패");
        when(file.getInputStream()).thenThrow(cause);

        assertThatThrownBy(() -> controller.generateStudyAsync(file))
                .isInstanceOfSatisfying(PdfExtractionException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("PDF_READ_FAILED");
                    assertThat(failure.getCause()).isSameAs(cause);
                });
        verifyNoInteractions(generateStudy, findStudy);
    }

    @Test
    @DisplayName("스트림 종료 실패 시 원인을 유지한 전용 예외로 변환한다")
    void translatesStreamCloseFailure() throws IOException {
        IOException cause = new IOException("스트림 종료 실패");
        InputStream input = new ByteArrayInputStream(new byte[]{1}) {
            @Override
            public void close() throws IOException {
                throw cause;
            }
        };
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(input);
        when(file.getOriginalFilename()).thenReturn("test.pdf");
        StudySession session = mock(StudySession.class);
        when(session.getId()).thenReturn(1L);
        when(generateStudy.generateStudyAsync("test.pdf", input)).thenReturn(session);
        when(findStudy.findById(1L)).thenReturn(new StudySessionView(
                1L, "test.pdf", null, null, "PENDING", false, null, List.of(), List.of()));

        assertThatThrownBy(() -> controller.generateStudyAsync(file))
                .isInstanceOfSatisfying(PdfExtractionException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("PDF_READ_FAILED");
                    assertThat(failure.getCause()).isSameAs(cause);
                });
        verify(generateStudy).generateStudyAsync("test.pdf", input);
    }
}
