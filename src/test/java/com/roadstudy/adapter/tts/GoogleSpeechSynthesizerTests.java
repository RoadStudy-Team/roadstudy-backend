package com.roadstudy.adapter.tts;

import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.protobuf.ByteString;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.global.exception.FailureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Google TTS 음성 합성 어댑터")
class GoogleSpeechSynthesizerTests {
    private static final byte[] AUDIO_BYTES = {(byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4};

    private final TextToSpeechClient client = mock(TextToSpeechClient.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<TextToSpeechClient> provider = mock(ObjectProvider.class);
    private final GoogleSpeechSynthesizer synthesizer = new GoogleSpeechSynthesizer(provider, "ko-KR-Test-A", 1.25);

    @BeforeEach
    void setUp() {
        when(provider.getObject()).thenReturn(client);
    }

    @Test
    @DisplayName("설정한 음성과 속도로 한국어 MP3 합성을 요청하고 음성 바이트를 반환한다")
    void synthesizesKoreanMp3() {
        when(client.synthesizeSpeech(any(SynthesizeSpeechRequest.class))).thenReturn(response(AUDIO_BYTES));

        assertThat(synthesizer.synthesize("설명 대본")).isEqualTo(AUDIO_BYTES);

        var request = ArgumentCaptor.forClass(SynthesizeSpeechRequest.class);
        verify(client).synthesizeSpeech(request.capture());
        assertThat(request.getValue().getInput().getText()).isEqualTo("설명 대본");
        assertThat(request.getValue().getVoice().getLanguageCode()).isEqualTo("ko-KR");
        assertThat(request.getValue().getVoice().getName()).isEqualTo("ko-KR-Test-A");
        assertThat(request.getValue().getAudioConfig().getAudioEncoding()).isEqualTo(AudioEncoding.MP3);
        assertThat(request.getValue().getAudioConfig().getSpeakingRate()).isEqualTo(1.25);
    }

    @Test
    @DisplayName("음성 데이터가 비어 있는 응답을 호출 실패로 처리한다")
    void rejectsEmptyAudio() {
        when(client.synthesizeSpeech(any(SynthesizeSpeechRequest.class))).thenReturn(response(new byte[0]));

        assertFailure(FailureType.EXTERNAL_SERVICE_FAILURE, "TTS_GENERATION_FAILED");
    }

    @ParameterizedTest
    @EnumSource(value = StatusCode.Code.class,
            names = {"INVALID_ARGUMENT", "UNAUTHENTICATED", "PERMISSION_DENIED", "RESOURCE_EXHAUSTED", "UNAVAILABLE", "INTERNAL"})
    @DisplayName("Google TTS의 API 오류를 서비스 호출 실패로 변환한다")
    void handlesApiError(StatusCode.Code code) {
        RuntimeException cause = apiException(code);
        when(client.synthesizeSpeech(any(SynthesizeSpeechRequest.class))).thenThrow(cause);

        assertThatExceptionOfType(StudyAudioException.class)
                .isThrownBy(() -> synthesizer.synthesize("설명 대본"))
                .withCause(cause)
                .satisfies(ex -> {
                    assertThat(ex.getFailureType()).isEqualTo(FailureType.EXTERNAL_SERVICE_FAILURE);
                    assertThat(ex.getCode()).isEqualTo("TTS_GENERATION_FAILED");
                    assertThat(ex.getMessage()).doesNotContain("upstream details");
                });
    }

    @Test
    @DisplayName("제한 시간 안에 응답하지 않으면 타임아웃으로 변환한다")
    void handlesTimeout() {
        when(client.synthesizeSpeech(any(SynthesizeSpeechRequest.class)))
                .thenThrow(apiException(StatusCode.Code.DEADLINE_EXCEEDED));

        assertFailure(FailureType.EXTERNAL_SERVICE_TIMEOUT, "TTS_TIMEOUT");
    }

    @Test
    @DisplayName("인증 설정 문제로 클라이언트를 만들지 못하면 호출 실패로 처리한다")
    void handlesClientCreationFailure() {
        when(provider.getObject()).thenThrow(new BeanCreationException("textToSpeechClient", "외부에 공개할 수 없는 인증 오류"));

        assertFailure(FailureType.EXTERNAL_SERVICE_FAILURE, "TTS_GENERATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.24, 2.01})
    @DisplayName("지원 범위를 벗어난 말하기 속도 설정을 거부한다")
    void rejectsUnsupportedSpeakingRate(double speakingRate) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GoogleSpeechSynthesizer(provider, "ko-KR-Test-A", speakingRate));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 2.0})
    @DisplayName("말하기 속도의 최솟값과 최댓값을 허용한다")
    void acceptsSpeakingRateBoundaries(double speakingRate) {
        var adapter = new GoogleSpeechSynthesizer(provider, "ko-KR-Test-A", speakingRate);
        when(client.synthesizeSpeech(any(SynthesizeSpeechRequest.class)))
                .thenReturn(response(AUDIO_BYTES));

        assertThat(adapter.synthesize("설명 대본")).isEqualTo(AUDIO_BYTES);
        var request = ArgumentCaptor.forClass(SynthesizeSpeechRequest.class);
        verify(client).synthesizeSpeech(request.capture());
        assertThat(request.getValue().getAudioConfig().getSpeakingRate()).isEqualTo(speakingRate);
    }

    private void assertFailure(FailureType type, String code) {
        assertThatExceptionOfType(StudyAudioException.class)
                .isThrownBy(() -> synthesizer.synthesize("설명 대본"))
                .satisfies(ex -> {
                    assertThat(ex.getFailureType()).isEqualTo(type);
                    assertThat(ex.getCode()).isEqualTo(code);
                    assertThat(ex.getMessage()).doesNotContain("upstream details", "인증 오류");
                });
    }

    private static SynthesizeSpeechResponse response(byte[] audio) {
        return SynthesizeSpeechResponse.newBuilder().setAudioContent(ByteString.copyFrom(audio)).build();
    }

    private static RuntimeException apiException(StatusCode.Code code) {
        var status = new StatusCode() {
            @Override
            public Code getCode() {
                return code;
            }

            @Override
            public Object getTransportCode() {
                return code;
            }
        };
        return ApiExceptionFactory.createException(new RuntimeException("upstream details"), status, false);
    }
}
