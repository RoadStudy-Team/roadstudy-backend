package com.roadstudy.adapter.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

@Tag("postgres")
@EnabledIfSystemProperty(named = "roadstudy.postgres", matches = "true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:${DB_PORT:5433}/roadstudy",
        "spring.datasource.username=roadstudy",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PostgresStudySessionPersistenceTests extends StudySessionPersistenceTests {
}
