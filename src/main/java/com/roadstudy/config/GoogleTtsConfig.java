package com.roadstudy.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class GoogleTtsConfig {
    @Bean(destroyMethod = "close")
    @Lazy
    TextToSpeechClient textToSpeechClient(@Value("${GOOGLE_APPLICATION_CREDENTIALS:}") String credentialsPath,
                                          @Value("${GOOGLE_TTS_TIMEOUT_SECONDS:60}") long timeoutSeconds) throws IOException {
        Assert.isTrue(timeoutSeconds > 0, "GOOGLE_TTS_TIMEOUT_SECONDS는 0보다 커야 합니다.");

        var settings = TextToSpeechSettings.newBuilder();
        // HTTP 요청 안에서 합성하므로 자동 재시도 없이 호출 제한 시간을 적용한다.
        settings.synthesizeSpeechSettings()
                .setSimpleTimeoutNoRetriesDuration(Duration.ofSeconds(timeoutSeconds));

        if (StringUtils.hasText(credentialsPath)) {
            try (var input = Files.newInputStream(Path.of(credentialsPath))) {
                var credentials = ServiceAccountCredentials.fromStream(input)
                        .createScoped(TextToSpeechSettings.getDefaultServiceScopes());
                settings.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
            }
        }

        return TextToSpeechClient.create(settings.build());
    }
}
