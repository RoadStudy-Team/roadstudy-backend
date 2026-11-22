package com.roadstudy.config;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class GoogleTtsConfig {
    @Bean(destroyMethod = "close")
    @Lazy
    TextToSpeechClient textToSpeechClient(@Value("${GOOGLE_APPLICATION_CREDENTIALS:}") String credentialsPath,
                                          @Value("${GOOGLE_TTS_TIMEOUT_SECONDS:60}") long timeoutSeconds) throws IOException {
        Assert.isTrue(timeoutSeconds > 0, "GOOGLE_TTS_TIMEOUT_SECONDS는 0보다 커야 합니다.");

        TextToSpeechSettings.Builder settings = TextToSpeechSettings.newBuilder();
        // SDK 자동 재시도 방지, 서비스에서 재시도 횟수 관리
        settings.synthesizeSpeechSettings()
                .setSimpleTimeoutNoRetriesDuration(Duration.ofSeconds(timeoutSeconds));

        if (StringUtils.hasText(credentialsPath)) {
            try (java.io.InputStream input = Files.newInputStream(Path.of(credentialsPath))) {
                com.google.auth.oauth2.GoogleCredentials credentials = ServiceAccountCredentials.fromStream(input)
                        .createScoped(TextToSpeechSettings.getDefaultServiceScopes());
                settings.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
            }
        }

        return TextToSpeechClient.create(settings.build());
    }
}
