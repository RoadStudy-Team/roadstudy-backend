package com.roadstudy.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class OpenAiConfig {
    @Bean
    @Lazy
    RestClient openAiRestClient(RestClient.Builder builder, @Value("${OPENAI_API_KEY:}") String apiKey,
                                @Value("${OPENAI_READ_TIMEOUT_SECONDS:120}") long readTimeoutSeconds) {
        Assert.hasText(apiKey, "OpenAI 클라이언트를 사용하려면 OPENAI_API_KEY 환경변수를 설정해야 합니다.");

        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return builder.baseUrl("https://api.openai.com/v1")
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBearerAuth(apiKey))
                .build();
    }
}
