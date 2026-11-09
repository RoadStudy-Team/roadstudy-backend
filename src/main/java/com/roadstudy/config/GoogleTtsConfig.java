package com.roadstudy.config;

import java.io.IOException;

import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration(proxyBeanMethods = false)
public class GoogleTtsConfig {
    @Bean(destroyMethod = "close")
    @Lazy
    TextToSpeechClient textToSpeechClient() throws IOException {
        return TextToSpeechClient.create();
    }
}
