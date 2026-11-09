package com.roadstudy.adapter.persistence;

import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudySessionCreateRequest;
import com.roadstudy.domain.study.StudyStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(StudyPersistenceAdapter.class)
@DisplayName("학습 세션 JPA 저장 어댑터")
class StudyPersistenceAdapterTests {
    @Autowired StudyRepository studyRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("학습 세션의 초기 상태와 실패 상태를 저장하고 조회한다")
    void savesAndLoadsSessionStatus() {
        var processing = studyRepository.save(StudySession.create(new StudySessionCreateRequest("lecture.pdf")));
        Long id = processing.getId();
        assertThat(id).isNotNull();

        entityManager.flush();
        entityManager.clear();
        var loaded = studyRepository.findById(id).orElseThrow();
        var createdAt = loaded.getCreatedAt();
        assertThat(createdAt).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(StudyStatus.PROCESSING);

        entityManager.detach(loaded);
        loaded.fail();
        studyRepository.save(loaded);

        entityManager.flush();
        entityManager.clear();
        var failed = studyRepository.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(StudyStatus.FAILED);
        assertThat(failed.getOriginalFileName()).isEqualTo("lecture.pdf");
        assertThat(failed.getCreatedAt()).isEqualTo(createdAt);
    }
}
