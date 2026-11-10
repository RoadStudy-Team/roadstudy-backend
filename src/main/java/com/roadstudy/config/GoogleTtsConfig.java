package com.roadstudy.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class GoogleTtsConfig {
    @Bean(destroyMethod = "close")
    @Lazy
    TextToSpeechClient textToSpeechClient(@Value("${GOOGLE_APPLICATION_CREDENTIALS:}") String credentialsPath) throws IOException {
        if (!StringUtils.hasText(credentialsPath)) {
            return TextToSpeechClient.create();
        }

        try (var input = Files.newInputStream(Path.of(credentialsPath))) {
            var credentials = ServiceAccountCredentials.fromStream(input)
                    .createScoped(TextToSpeechSettings.getDefaultServiceScopes());

            var settings = TextToSpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();

            return TextToSpeechClient.create(settings);
        }
    }
}
