package com.roadstudy.config;

import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
class LocalIntegrationStartupCheck implements ApplicationRunner {
    private final ApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        checkClient("OpenAI", "openAiRestClient", RestClient.class, "OPENAI_API_KEY");
        checkClient("Google TTS", "textToSpeechClient", TextToSpeechClient.class, "GOOGLE_APPLICATION_CREDENTIALS");
    }

    private void checkClient(String name, String beanName, Class<?> type, String setting) {
        try {
            context.getBean(beanName, type);
            log.info("{} 클라이언트 빈 초기화 완료. API 호출 검증은 생략했습니다.", name);
        } catch (BeansException ex) {
            log.warn("{} 클라이언트 빈 초기화 실패. {} 설정을 확인해 주세요. 예외 유형 {}",
                    name, setting, ex.getMostSpecificCause().getClass().getSimpleName());
        }
    }
}
