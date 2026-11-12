package com.roadstudy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Swagger UI와 OpenAPI 문서를 제공한다")
    void exposesSwagger() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("RoadStudy API"))
                .andExpect(jsonPath("$.paths['/api/studies/extract-text'].post.summary").value("PDF 텍스트 추출"))
                .andExpect(jsonPath("$.paths['/api/studies/extract-text'].post.requestBody"
                        + ".content['multipart/form-data'].schema.properties.file.format").value("binary"))
                .andExpect(jsonPath("$.paths['/api/studies/extract-text'].post.responses['422']"
                        + ".content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/studies/generate-audio'].post.summary").value("학습 음성 생성 (임시)"))
                .andExpect(jsonPath("$.paths['/api/studies/generate-audio'].post.requestBody"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/GenerateAudioRequest"))
                .andExpect(jsonPath("$.paths['/api/studies/audio/{audioId}'].get.responses['200']"
                        + ".content['audio/mpeg'].schema.format").value("binary"))
                .andExpect(jsonPath("$.paths['/api/studies/audio/{audioId}'].get.responses['404']"
                        + ".content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/studies/audio/{audioId}'].get.responses['206']"
                        + ".content['audio/mpeg'].schema.format").value("binary"))
                .andExpect(jsonPath("$.paths['/api/studies/audio/{audioId}'].get.responses['416']"
                        + ".content['application/problem+json']").exists());
    }

}
