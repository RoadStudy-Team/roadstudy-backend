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
    RestClient openAiRestClient(RestClient.Builder builder, @Value("${OPENAI_API_KEY:}") String apiKey) {
        Assert.hasText(apiKey, "Set OPENAI_API_KEY to use the OpenAI client.");

        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(45));

        return builder.baseUrl("https://api.openai.com/v1")
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBearerAuth(apiKey))
                .build();
    }
}
