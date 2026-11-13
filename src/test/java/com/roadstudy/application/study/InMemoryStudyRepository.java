package com.roadstudy.application.study;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.roadstudy.application.study.port.out.StudyRepository;
import com.roadstudy.domain.study.StudySession;
import com.roadstudy.domain.study.StudyStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** 저장할 때마다 ID를 부여하고 그 시점의 상태를 기록하는 테스트용 저장소. */
public class InMemoryStudyRepository implements StudyRepository {
    private final Map<Long, StudySession> sessions = new HashMap<>();
    private final List<StudyStatus> savedStatuses = new ArrayList<>();
    private long sequence;

    @Override
    public StudySession save(StudySession study) {
        if (study.getId() == null) {
            ReflectionTestUtils.setField(study, "id", ++sequence);
        }
        sessions.put(study.getId(), study);
        savedStatuses.add(study.getStatus());
        return study;
    }

    @Override
    public Optional<StudySession> findById(Long id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public List<StudySession> findAllByCreatedAtDescending() {
        return sessions.values().stream()
                .sorted(Comparator.comparing(StudySession::getCreatedAt).thenComparing(StudySession::getId).reversed())
                .toList();
    }

    public List<StudyStatus> savedStatuses() {
        return List.copyOf(savedStatuses);
    }
}
