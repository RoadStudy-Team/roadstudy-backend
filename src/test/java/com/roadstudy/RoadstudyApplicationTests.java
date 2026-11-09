package com.roadstudy;

import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:roadstudy;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "OPENAI_API_KEY=test-key"
})
@AutoConfigureMockMvc
@DisplayName("애플리케이션 통합 테스트")
class RoadstudyApplicationTests {
    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Autowired RestClient openAiRestClient;

    @Test
    @DisplayName("Swagger UI와 OpenAPI 문서를 제공한다")
    void exposesSwagger() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("RoadStudy API"));
        assertThat(openAiRestClient).isNotNull();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GOOGLE_APPLICATION_CREDENTIALS", matches = ".+")
    @DisplayName("외부 API 호출 없이 Google TTS 클라이언트 빈을 생성한다")
    void createsGoogleTtsClientWithoutMakingApiCalls() {
        assertThat(context.getBean(TextToSpeechClient.class)).isNotNull();
    }
}
