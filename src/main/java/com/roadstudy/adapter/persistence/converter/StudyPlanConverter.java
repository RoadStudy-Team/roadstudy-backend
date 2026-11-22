package com.roadstudy.adapter.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadstudy.application.study.exception.StudyGenerationException;
import com.roadstudy.domain.study.StudyPlan;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Converter(autoApply = true)
@Component
@RequiredArgsConstructor
public class StudyPlanConverter implements AttributeConverter<StudyPlan, String> {
    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(StudyPlan plan) {
        if (plan == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException ex) {
            throw StudyGenerationException.invalidStoredPlan();
        }
    }

    @Override
    public StudyPlan convertToEntityAttribute(String content) {
        if (content == null) {
            return null;
        }

        try {
            return objectMapper.readValue(content, StudyPlan.class);
        } catch (JsonProcessingException ex) {
            throw StudyGenerationException.invalidStoredPlan();
        }
    }
}
