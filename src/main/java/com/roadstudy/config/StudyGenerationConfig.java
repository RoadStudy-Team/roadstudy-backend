package com.roadstudy.config;

import com.roadstudy.application.study.model.StudyGenerationSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
public class StudyGenerationConfig {
    @Bean
    StudyGenerationSettings studyGenerationSettings(
            @Value("${STUDY_GENERATION_TOKEN_BUDGET:300000}") long tokenBudget,
            @Value("${STUDY_GENERATION_CALL_LIMIT:80}") int callLimit,
            @Value("${STUDY_GENERATION_RETRY_DELAY_MS:1000}") long retryDelayMillis) {
        return new StudyGenerationSettings(tokenBudget, callLimit, retryDelayMillis);
    }

    @Bean
    ThreadPoolTaskExecutor studyGenerationExecutor(@Value("${STUDY_GENERATION_THREADS:2}") int threads,
            @Value("${STUDY_GENERATION_QUEUE_CAPACITY:32}") int capacity) {
        Assert.isTrue(threads > 0, "STUDY_GENERATION_THREADS는 0보다 커야 합니다.");
        Assert.isTrue(capacity >= 0, "STUDY_GENERATION_QUEUE_CAPACITY는 음수일 수 없습니다.");

        // 유료 외부 API를 동시에 호출하는 수를 스레드 수로 제한하고, 나머지 요청은 대기열에서 기다린다.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(capacity);
        executor.setThreadNamePrefix("study-generation-");
        return executor;
    }
}
