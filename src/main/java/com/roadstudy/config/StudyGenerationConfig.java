package com.roadstudy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
public class StudyGenerationConfig {
    @Bean
    ThreadPoolTaskExecutor studyGenerationExecutor(@Value("${STUDY_GENERATION_THREADS:2}") int threads) {
        Assert.isTrue(threads > 0, "STUDY_GENERATION_THREADS는 0보다 커야 합니다.");

        // 유료 외부 API를 동시에 호출하는 수를 스레드 수로 제한하고, 나머지 요청은 대기열에서 기다린다.
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setThreadNamePrefix("study-generation-");
        return executor;
    }
}
