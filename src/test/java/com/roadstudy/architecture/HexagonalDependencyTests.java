package com.roadstudy.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("헥사고날 계층의 import 방향")
class HexagonalDependencyTests {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/roadstudy");

    @Test
    @DisplayName("도메인은 Application과 외부 어댑터를 참조하지 않는다")
    void domainDoesNotImportOuterLayers() throws IOException {
        assertNoImports("domain", List.of(
                "com.roadstudy.application.", "com.roadstudy.adapter.", "com.roadstudy.config.",
                "org.springframework.", "com.fasterxml.", "com.google.", "org.apache.pdfbox.",
                "com.roadstudy.global.exception.GlobalExceptionHandler"));
    }

    @Test
    @DisplayName("Application은 외부 구현체와 HTTP·JPA 기술을 참조하지 않는다")
    void applicationDependsOnPortsAndDomain() throws IOException {
        assertNoImports("application", List.of(
                "com.roadstudy.adapter.", "com.roadstudy.config.",
                "org.springframework.web.", "org.springframework.http.", "org.springframework.core.io.",
                "org.springframework.data.", "jakarta.persistence.",
                "com.fasterxml.", "com.google.", "org.apache.pdfbox.",
                "com.roadstudy.global.exception.GlobalExceptionHandler"));
    }

    @Test
    @DisplayName("Web은 서비스 구현체나 Outbound Port를 직접 참조하지 않는다")
    void webUsesInboundPorts() throws IOException {
        assertNoImports("adapter/web", List.of(
                "com.roadstudy.application.study.service.", "com.roadstudy.application.study.port.out.",
                "com.roadstudy.adapter.persistence.", "com.roadstudy.adapter.storage.",
                "com.roadstudy.adapter.pdf.", "com.roadstudy.adapter.openai.", "com.roadstudy.adapter.tts."));
    }

    private void assertNoImports(String packagePath, List<String> forbiddenPrefixes) throws IOException {
        Path directory = SOURCE_ROOT.resolve(packagePath);
        assertThat(directory).isDirectory();
        List<Path> files;
        try (Stream<Path> paths = Files.walk(directory)) {
            files = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        assertThat(files).isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            for (String line : Files.readAllLines(file)) {
                String statement = line.strip();
                if (!statement.startsWith("import ")) {
                    continue;
                }
                String importedType = statement.substring(7).replaceFirst("^static ", "").replace(";", "").strip();
                for (String prefix : forbiddenPrefixes) {
                    if (importedType.startsWith(prefix)) {
                        violations.add(file + ": " + statement);
                    }
                }
            }
        }
        assertThat(violations).as("계층을 거슬러 참조한 import").isEmpty();
    }
}
