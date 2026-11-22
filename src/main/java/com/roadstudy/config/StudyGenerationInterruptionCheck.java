package com.roadstudy.config;

import com.roadstudy.application.study.port.in.GenerateStudy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudyGenerationInterruptionCheck {
    private final GenerateStudy generateStudy;

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void interruptUnfinishedWork() {
        generateStudy.interruptGeneration();
    }
}
