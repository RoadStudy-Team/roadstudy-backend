package com.roadstudy.adapter.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.model.StudyDocument;
import com.roadstudy.application.study.port.out.DocumentReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("로컬 학습 자료 저장 어댑터")
class LocalStudySourceStorageTests {
    @TempDir Path directory;
    private final DocumentReader reader = mock(DocumentReader.class);
    private final StudyDocument document = new StudyDocument(List.of(new StudyDocument.Page(1, "본문")));

    @Test
    void preservesOriginalAndCachesExtractionWithoutReadingPdfAgain() throws Exception {
        byte[] content = {1, 2, 3};
        when(reader.read(content)).thenReturn(document);
        LocalStudySourceStorage storage = new LocalStudySourceStorage(directory.toString(), reader, new ObjectMapper());
        String key = storage.save(UUID.randomUUID(), content);
        assertThat(Files.readAllBytes(directory.resolve(key))).isEqualTo(content);
        assertThat(storage.read(key)).isEqualTo(document);
        Files.delete(directory.resolve(key));
        assertThat(storage.read(key)).isEqualTo(document);
        verify(reader, times(1)).read(content);
    }

    @Test
    void rejectsTraversalBeforeCallingDocumentReader() {
        LocalStudySourceStorage storage = new LocalStudySourceStorage(directory.toString(), reader, new ObjectMapper());
        assertThatThrownBy(() -> storage.read("../outside.pdf")).isInstanceOf(PdfExtractionException.class);
        verifyNoInteractions(reader);
    }

    @Test
    void propagatesPdfFailureWithoutWritingCache() {
        byte[] content = {1};
        PdfExtractionException failure = PdfExtractionException.emptyText();
        when(reader.read(content)).thenThrow(failure);
        LocalStudySourceStorage storage = new LocalStudySourceStorage(directory.toString(), reader, new ObjectMapper());
        String key = storage.save(UUID.randomUUID(), content);
        assertThatThrownBy(() -> storage.read(key)).isSameAs(failure);
        assertThat(directory.resolve(key + ".json")).doesNotExist();
    }

    @Test
    void deletesOriginalAndExtractedDocumentTogether() {
        byte[] content = {1};
        when(reader.read(content)).thenReturn(document);
        LocalStudySourceStorage storage = new LocalStudySourceStorage(directory.toString(), reader, new ObjectMapper());
        String key = storage.save(UUID.randomUUID(), content);
        storage.read(key);
        storage.delete(key);
        assertThat(directory.resolve(key)).doesNotExist();
        assertThat(directory.resolve(key + ".json")).doesNotExist();
    }

    @Test
    void translatesMissingOriginalFileWithoutCallingReader() {
        LocalStudySourceStorage storage = new LocalStudySourceStorage(directory.toString(), reader, new ObjectMapper());
        assertThatThrownBy(() -> storage.read(UUID.randomUUID() + ".pdf"))
                .isInstanceOfSatisfying(PdfExtractionException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo("PDF_READ_FAILED"));
        verifyNoInteractions(reader);
    }
}
