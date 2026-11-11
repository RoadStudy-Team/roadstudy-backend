package com.roadstudy.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("Google TTS 클라이언트 설정")
class GoogleTtsConfigTests {
    @TempDir Path directory;

    @Test
    @DisplayName("기본 인증을 사용할 때도 설정한 제한 시간과 단일 시도를 적용한다")
    void appliesTimeoutWithDefaultCredentials() throws IOException {
        TextToSpeechClient client = mock(TextToSpeechClient.class);
        try (var factory = mockStatic(TextToSpeechClient.class)) {
            factory.when(() -> TextToSpeechClient.create(any(TextToSpeechSettings.class))).thenReturn(client);

            assertThat(new GoogleTtsConfig().textToSpeechClient("", 37)).isSameAs(client);

            var settings = ArgumentCaptor.forClass(TextToSpeechSettings.class);
            factory.verify(() -> TextToSpeechClient.create(settings.capture()));
            assertTimeout(settings.getValue(), 37);
        }
    }

    @Test
    @DisplayName("서비스 계정 인증과 호출 제한 시간을 같은 클라이언트 설정에 적용한다")
    void appliesCredentialsAndTimeoutTogether() throws IOException {
        Path credentialsFile = Files.writeString(directory.resolve("service-account.json"), "{}");
        var credentials = mock(ServiceAccountCredentials.class);
        var scopedCredentials = mock(ServiceAccountCredentials.class);
        var client = mock(TextToSpeechClient.class);
        when(credentials.createScoped(anyCollection())).thenReturn(scopedCredentials);

        try (var credentialFactory = mockStatic(ServiceAccountCredentials.class);
             var clientFactory = mockStatic(TextToSpeechClient.class)) {
            credentialFactory.when(() -> ServiceAccountCredentials.fromStream(any(InputStream.class)))
                    .thenReturn(credentials);
            clientFactory.when(() -> TextToSpeechClient.create(any(TextToSpeechSettings.class)))
                    .thenReturn(client);

            assertThat(new GoogleTtsConfig().textToSpeechClient(credentialsFile.toString(), 60)).isSameAs(client);

            var settings = ArgumentCaptor.forClass(TextToSpeechSettings.class);
            clientFactory.verify(() -> TextToSpeechClient.create(settings.capture()));
            assertThat(settings.getValue().getCredentialsProvider().getCredentials()).isSameAs(scopedCredentials);
            assertTimeout(settings.getValue(), 60);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("0 이하의 호출 제한 시간 설정을 거부한다")
    void rejectsNonPositiveTimeout(long timeoutSeconds) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GoogleTtsConfig().textToSpeechClient("", timeoutSeconds))
                .withMessage("GOOGLE_TTS_TIMEOUT_SECONDS는 0보다 커야 합니다.");
    }

    private static void assertTimeout(TextToSpeechSettings settings, long seconds) {
        var retry = settings.synthesizeSpeechSettings().getRetrySettings();
        assertThat(retry.getTotalTimeoutDuration()).isEqualTo(Duration.ofSeconds(seconds));
        assertThat(retry.getInitialRpcTimeoutDuration()).isEqualTo(Duration.ofSeconds(seconds));
        assertThat(retry.getMaxRpcTimeoutDuration()).isEqualTo(Duration.ofSeconds(seconds));
        assertThat(retry.getMaxAttempts()).isEqualTo(1);
    }
}
