package com.roadstudy.application.study.port.out;

import com.roadstudy.application.study.model.*;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.StudyPlan;

public interface StudyGenerator {
    StudyPlan generatePlan(StudyDocument document, GenerationBudget budget);

    String generateExplanation(StudyDocument document, StudyPlan plan, int chapterIndex,
                               AudioVariant variant, GenerationBudget budget);

    String reviewExplanation(StudyDocument document, StudyPlan plan, int chapterIndex,
                             String script, GenerationBudget budget);
}
