package com.roadstudy.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.global.exception.FailureType;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("로컬 음성 파일 저장 어댑터")
class LocalAudioStorageTests {
    private static final byte[] AUDIO_BYTES = {(byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4};

    @TempDir Path root;

    @Test
    @DisplayName("저장 디렉터리가 없으면 만들고 UUID 이름의 MP3 파일로 저장한다")
    void createsDirectoryAndSavesMp3() throws IOException {
        Path directory = root.resolve("storage").resolve("audio");
        UUID audioId = UUID.randomUUID();

        var saved = new LocalAudioStorage(directory.toString()).save(audioId, AUDIO_BYTES);

        assertThat(saved.audioId()).isEqualTo(audioId);
        assertThat(saved.path()).isEqualTo(directory.resolve(audioId + ".mp3")).isAbsolute();
        assertThat(Files.readAllBytes(saved.path())).isEqualTo(AUDIO_BYTES);
        assertThat(directory).isDirectoryNotContaining("glob:**.tmp");
    }

    @Test
    @DisplayName("저장한 음성을 ID로 조회한다")
    void findsSavedAudio() {
        var storage = new LocalAudioStorage(root.toString());
        var saved = storage.save(UUID.randomUUID(), AUDIO_BYTES);

        assertThat(storage.find(saved.audioId())).contains(saved);
    }

    @Test
    @DisplayName("저장한 적 없는 ID는 조회되지 않는다")
    void doesNotFindUnknownAudio() {
        assertThat(new LocalAudioStorage(root.toString()).find(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("쓰는 중인 임시 파일은 음성으로 조회되지 않는다")
    void doesNotFindIncompleteFile() throws IOException {
        UUID audioId = UUID.randomUUID();
        Files.write(root.resolve(audioId + "-123.tmp"), AUDIO_BYTES);

        assertThat(new LocalAudioStorage(root.toString()).find(audioId)).isEmpty();
    }

    @Test
    @DisplayName("최종 파일로 옮기지 못하면 임시 파일을 지우고 저장 실패를 알린다")
    void cleansUpIncompleteFileWhenSaveFails() throws IOException {
        UUID audioId = UUID.randomUUID();
        // 최종 파일 자리에 비어 있지 않은 디렉터리를 두어 이름 바꾸기를 실패시킨다.
        Files.createDirectories(root.resolve(audioId + ".mp3").resolve("occupied"));
        var storage = new LocalAudioStorage(root.toString());

        assertStorageFailure(() -> storage.save(audioId, AUDIO_BYTES));

        assertThat(root).isDirectoryNotContaining("glob:**.tmp");
        assertThat(storage.find(audioId)).isEmpty();
    }

    @Test
    @DisplayName("저장 경로를 디렉터리로 만들 수 없으면 저장 실패를 알린다")
    void failsWhenDirectoryCannotBeCreated() throws IOException {
        Path file = Files.createFile(root.resolve("not-a-directory"));
        var storage = new LocalAudioStorage(file.resolve("audio").toString());

        assertStorageFailure(() -> storage.save(UUID.randomUUID(), AUDIO_BYTES));
    }

    private static void assertStorageFailure(ThrowingCallable save) {
        assertThatExceptionOfType(StudyAudioException.class)
                .isThrownBy(save)
                .satisfies(ex -> {
                    assertThat(ex.getFailureType()).isEqualTo(FailureType.INTERNAL_FAILURE);
                    assertThat(ex.getCode()).isEqualTo("AUDIO_STORAGE_FAILED");
                    assertThat(ex.getCause()).isInstanceOf(IOException.class);
                });
    }
}
