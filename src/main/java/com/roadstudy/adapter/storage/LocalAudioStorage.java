package com.roadstudy.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

import com.roadstudy.application.study.StudyAudio;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.port.out.AudioStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocalAudioStorage implements AudioStorage {
    private static final String EXTENSION = ".mp3";

    private final Path directory;

    public LocalAudioStorage(@Value("${AUDIO_STORAGE_PATH:storage/audio}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public StudyAudio save(UUID audioId, byte[] content) {
        Path target = pathOf(audioId);
        Path temp = null;
        try {
            Files.createDirectories(directory);
            // 쓰는 도중의 파일이 조회되지 않도록 임시 파일에 다 쓴 뒤 최종 이름으로 한 번에 바꾼다.
            temp = Files.createTempFile(directory, audioId + "-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return new StudyAudio(audioId, target);
        } catch (IOException ex) {
            deleteQuietly(temp);
            log.error("음성 파일을 저장하지 못했습니다. 예외 유형 {}", ex.getClass().getSimpleName());
            throw StudyAudioException.storageFailed(ex);
        }
    }

    @Override
    public Optional<StudyAudio> find(UUID audioId) {
        Path path = pathOf(audioId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        return Optional.of(new StudyAudio(audioId, path));
    }

    private Path pathOf(UUID audioId) {
        return directory.resolve(audioId + EXTENSION);
    }

    private void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ex) {
            log.warn("저장에 실패한 임시 음성 파일을 지우지 못했습니다. 예외 유형 {}", ex.getClass().getSimpleName());
        }
    }
}
