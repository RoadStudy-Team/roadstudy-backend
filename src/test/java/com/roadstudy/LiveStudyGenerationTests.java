package com.roadstudy;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.*;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@Tag("live")
@EnabledIfSystemProperty(named = "roadstudy.live", matches = "true")
@SpringBootTest(properties = {
        "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:live-study;MODE=PostgreSQL",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "STUDY_SOURCE_PATH=build/live-study/sources", "AUDIO_STORAGE_PATH=build/live-study/audio"})
@AutoConfigureMockMvc
class LiveStudyGenerationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Test
    void evaluatesRepresentativePdfWithRealProviders() throws Exception {
        Path pdf = Path.of(System.getProperty("roadstudy.pdf"));
        String location = mvc.perform(multipart("/api/studies").file(new MockMultipartFile(
                "file", pdf.getFileName().toString(), "application/pdf", Files.readAllBytes(pdf))))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        Path report = Path.of("build/live-study/result.json");
        Files.createDirectories(report.getParent());
        await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofMinutes(20)).until(() -> {
            JsonNode result = mapper.readTree(mvc.perform(get(location)).andReturn().getResponse().getContentAsString());
            Files.writeString(report, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            String status = result.path("status").asText();
            return status.equals("COMPLETED") || status.equals("FAILED")
                    || status.equals("PARTIALLY_READY") && result.path("chapters").findValuesAsText("status").stream()
                    .noneMatch(value -> value.equals("PENDING") || value.equals("GENERATING"));
        });
        JsonNode result = mapper.readTree(Files.readString(report));
        assertThat(result.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("chapters").size()).isPositive();
        for (JsonNode chapter : result.path("chapters")) {
            JsonNode audio = chapter.path("audios").get(0);
            assertThat(audio.path("script").asText()).isNotBlank();
            byte[] bytes = mvc.perform(get(audio.path("audioUrl").asText())).andReturn().getResponse().getContentAsByteArray();
            assertThat(bytes.length).isGreaterThan(1000);
            Files.write(report.resolveSibling("chapter-" + chapter.path("order").asInt() + ".mp3"), bytes);
        }
    }
}
