package com.roadstudy.adapter.persistence;

import com.roadstudy.domain.study.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;

interface StudyJpaRepository extends JpaRepository<StudySession, Long> {
}
