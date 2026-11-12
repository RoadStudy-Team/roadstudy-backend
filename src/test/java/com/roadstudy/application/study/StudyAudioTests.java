package com.roadstudy.application.study;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import com.roadstudy.application.study.port.out.StudyGenerator;
import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.global.exception.FailureType;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("학습 음성 생성과 조회")
class StudyAudioTests {
    private static final byte[] AUDIO_BYTES = {(byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4};
    // 한글 1,666자(4,998바이트)에 ASCII 2자를 더해 정확히 5,000바이트를 만든다.
    private static final String SCRIPT_AT_LIMIT = "가".repeat(1_666) + "ab";

    private final SpeechSynthesizer synthesizer = mock(SpeechSynthesizer.class);
    private final AudioStorage storage = mock(AudioStorage.class);
    private final StudyModifyService modifyService = new StudyModifyService(
            mock(DocumentReader.class), mock(StudyGenerator.class), mock(StudyRepository.class), synthesizer, storage);
    private final StudyQueryService queryService = new StudyQueryService(storage);

    @Test
    @DisplayName("앞뒤 공백을 뺀 대본을 합성하고 새 ID로 저장한 결과를 반환한다")
    void synthesizesAndStoresAudio() {
        when(synthesizer.synthesize("설명 대본")).thenReturn(AUDIO_BYTES);
        when(storage.save(any(UUID.class), eq(AUDIO_BYTES)))
                .thenAnswer(call -> new StudyAudio(call.getArgument(0), Path.of("audio.mp3")));

        var first = modifyService.generate(" 설명 대본\n");
        var second = modifyService.generate("설명 대본");

        assertThat(first.path()).isEqualTo(Path.of("audio.mp3"));
        assertThat(first.audioId()).isNotNull().isNotEqualTo(second.audioId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "\n\t"})
    @DisplayName("대본이 없거나 공백뿐이면 합성기를 호출하지 않고 거부한다")
    void rejectsEmptyScript(String script) {
        assertFailure(() -> modifyService.generate(script), FailureType.INVALID_INPUT, "EMPTY_SCRIPT");
        verifyNoInteractions(synthesizer, storage);
    }

    @Test
    @DisplayName("UTF-8 기준 정확히 5,000바이트인 대본을 허용한다")
    void acceptsScriptAtByteLimit() {
        assertThat(SCRIPT_AT_LIMIT.getBytes(StandardCharsets.UTF_8)).hasSize(5_000);
        when(synthesizer.synthesize(SCRIPT_AT_LIMIT)).thenReturn(AUDIO_BYTES);
        when(storage.save(any(UUID.class), eq(AUDIO_BYTES)))
                .thenAnswer(call -> new StudyAudio(call.getArgument(0), Path.of("audio.mp3")));

        assertThat(modifyService.generate(SCRIPT_AT_LIMIT).audioId()).isNotNull();
    }

    @Test
    @DisplayName("5,000바이트보다 1바이트 길면 합성기 호출 전에 거부한다")
    void rejectsScriptOverByteLimit() {
        assertFailure(() -> modifyService.generate(SCRIPT_AT_LIMIT + "c"),
                FailureType.UNPROCESSABLE_CONTENT, "TTS_INPUT_TOO_LONG");
        verifyNoInteractions(synthesizer, storage);
    }

    @Test
    @DisplayName("글자 수가 아니라 바이트로 판단하므로 한글 1,667자는 거부한다")
    void countsBytesNotCharacters() {
        assertFailure(() -> modifyService.generate("가".repeat(1_667)),
                FailureType.UNPROCESSABLE_CONTENT, "TTS_INPUT_TOO_LONG");
    }

    @Test
    @DisplayName("음성 합성에 실패하면 파일을 저장하지 않는다")
    void doesNotStoreWhenSynthesisFails() {
        when(synthesizer.synthesize("설명 대본")).thenThrow(StudyAudioException.generationTimedOut());

        assertFailure(() -> modifyService.generate("설명 대본"), FailureType.EXTERNAL_SERVICE_TIMEOUT, "TTS_TIMEOUT");
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("저장된 음성을 ID 문자열로 조회한다")
    void findsStoredAudio() {
        var audio = new StudyAudio(UUID.randomUUID(), Path.of("audio.mp3"));
        when(storage.find(audio.audioId())).thenReturn(Optional.of(audio));

        assertThat(queryService.find(audio.audioId().toString())).isEqualTo(audio);
    }

    @Test
    @DisplayName("저장되지 않은 ID를 조회하면 음성 없음으로 처리한다")
    void rejectsUnknownAudio() {
        UUID audioId = UUID.randomUUID();
        when(storage.find(audioId)).thenReturn(Optional.empty());

        assertFailure(() -> queryService.find(audioId.toString()), FailureType.NOT_FOUND, "AUDIO_NOT_FOUND");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"not-a-uuid", "../secret", "..\\secret.mp3",
            "550e8400e29b41d4a716446655440000", "1-1-1-1-1"})
    @DisplayName("UUID 형식이 아닌 ID는 저장소를 조회하지 않고 음성 없음으로 처리한다")
    void rejectsMalformedAudioId(String audioId) {
        assertFailure(() -> queryService.find(audioId), FailureType.NOT_FOUND, "AUDIO_NOT_FOUND");
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("대문자로 표현한 표준 UUID로도 저장된 음성을 조회한다")
    void findsAudioWithUppercaseUuid() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        var audio = new StudyAudio(id, Path.of("audio.mp3"));
        when(storage.find(id)).thenReturn(Optional.of(audio));

        assertThat(queryService.find("550E8400-E29B-41D4-A716-446655440000")).isEqualTo(audio);
    }

    private static void assertFailure(ThrowingCallable call, FailureType type, String code) {
        assertThatExceptionOfType(StudyAudioException.class)
                .isThrownBy(call)
                .satisfies(ex -> {
                    assertThat(ex.getFailureType()).isEqualTo(type);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
    }
}
