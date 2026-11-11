package com.roadstudy.adapter.tts;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
public class GoogleSpeechSynthesizer implements SpeechSynthesizer {
    private static final String LANGUAGE_CODE = "ko-KR";

    private final ObjectProvider<TextToSpeechClient> clientProvider;
    private final VoiceSelectionParams voice;
    private final AudioConfig audioConfig;

    // TextToSpeechClient의 호출 메서드는 final이라 @Lazy 프록시가 가로채지 못한다. 첫 호출 때 빈을 직접 꺼낸다.
    public GoogleSpeechSynthesizer(ObjectProvider<TextToSpeechClient> clientProvider,
                                   @Value("${GOOGLE_TTS_VOICE:ko-KR-Neural2-A}") String voiceName,
                                   @Value("${GOOGLE_TTS_SPEAKING_RATE:1.0}") double speakingRate) {
        Assert.hasText(voiceName, "GOOGLE_TTS_VOICE에 음성 이름을 설정해야 합니다.");
        Assert.isTrue(speakingRate >= 0.25 && speakingRate <= 2.0,
                "GOOGLE_TTS_SPEAKING_RATE는 0.25 이상 2.0 이하로 설정해야 합니다.");

        this.clientProvider = clientProvider;
        this.voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(LANGUAGE_CODE)
                .setName(voiceName)
                .build();
        this.audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3)
                .setSpeakingRate(speakingRate)
                .build();
    }

    @Override
    public byte[] synthesize(String script) {
        var request = SynthesizeSpeechRequest.newBuilder()
                .setInput(SynthesisInput.newBuilder().setText(script))
                .setVoice(voice)
                .setAudioConfig(audioConfig)
                .build();

        ByteString audio;
        try {
            audio = clientProvider.getObject()
                    .synthesizeSpeech(request)
                    .getAudioContent();
        } catch (ApiException ex) {
            StatusCode.Code status = ex.getStatusCode().getCode();
            log.warn("Google TTS 호출에 실패했습니다. 상태 코드 {}", status);
            if (status == StatusCode.Code.DEADLINE_EXCEEDED) {
                throw StudyAudioException.generationTimedOut(ex);
            }
            throw StudyAudioException.generationFailed(ex);
        } catch (BeansException ex) {
            log.warn("Google TTS 클라이언트를 초기화하지 못했습니다. GOOGLE_APPLICATION_CREDENTIALS 설정을 확인해 주세요. 예외 유형 {}",
                    ex.getMostSpecificCause().getClass().getSimpleName());
            throw StudyAudioException.generationFailed(ex);
        }

        if (audio.isEmpty()) {
            throw StudyAudioException.generationFailed();
        }
        return audio.toByteArray();
    }
}
