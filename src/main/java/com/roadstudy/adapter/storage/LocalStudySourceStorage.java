package com.roadstudy.adapter.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.model.StudyDocument;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.StudySourceStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class LocalStudySourceStorage implements StudySourceStorage {
    private final Path directory;
    private final DocumentReader documentReader;
    private final ObjectMapper objectMapper;

    public LocalStudySourceStorage(@Value("${STUDY_SOURCE_PATH:storage/sources}") String directory,
                                   DocumentReader documentReader, ObjectMapper objectMapper) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.documentReader = documentReader;
        this.objectMapper = objectMapper;
    }

    @Override
    public String save(UUID id, byte[] pdf) {
        String key = id + ".pdf";
        Path temporary = null;

        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, id.toString(), ".tmp");
            Files.write(temporary, pdf);
            Files.move(temporary, resolve(key), StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch (IOException ex) {
            throw PdfExtractionException.readFailed(ex);
        } finally {
            deleteTemporary(temporary);
        }
    }

    @Override
    public StudyDocument read(String key) {
        Path source = resolve(key);
        Path cache = cachePath(source);
        if (Files.isRegularFile(cache)) {
            return readCached(cache);
        }
        StudyDocument document = documentReader.read(readContent(source));
        saveDocument(cache, document);
        return document;
    }

    @Override
    public void delete(String key) {
        Path source = resolve(key);
        try {
            Files.deleteIfExists(cachePath(source));
            Files.deleteIfExists(source);
        } catch (IOException ex) {
            throw PdfExtractionException.readFailed(ex);
        }
    }

    private StudyDocument readCached(Path cache) {
        try {
            return objectMapper.readValue(Files.readString(cache), StudyDocument.class);
        } catch (IOException ex) {
            throw PdfExtractionException.readFailed(ex);
        }
    }

    private byte[] readContent(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException ex) {
            throw PdfExtractionException.readFailed(ex);
        }
    }

    private void saveDocument(Path cache, StudyDocument document) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, "pages-", ".tmp");
            Files.writeString(temporary, objectMapper.writeValueAsString(document));
            Files.move(temporary, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw PdfExtractionException.readFailed(ex);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private Path cachePath(Path source) {
        return source.resolveSibling(source.getFileName() + ".json");
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.pdf")) {
            throw PdfExtractionException.invalidPdf(null);
        }

        Path target = directory.resolve(key).normalize();
        if (!target.getParent().equals(directory)) {
            throw PdfExtractionException.invalidPdf(null);
        }

        return target;
    }

    private void deleteTemporary(Path temporary) {
        if (temporary != null) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }
}
