package com.roadstudy.domain.study;

import com.roadstudy.domain.study.enums.GenerationStage;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.domain.study.exception.InvalidStudyAudioException;
import com.roadstudy.domain.study.exception.InvalidStudyStatusTransitionException;
import com.roadstudy.domain.study.request.StudyAudioCreateRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "study_audios", uniqueConstraints = @UniqueConstraint(columnNames = {"chapter_id", "variant"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyAudio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(name = "chapter_id", nullable = false)
    private Long chapterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", insertable = false, updatable = false)
    private StudyChapter chapter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudioVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudioStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GenerationStage stage;

    @Column(columnDefinition = "text")
    private String script;

    private String storageKey;

    @Column(nullable = false, updatable = false, unique = true)
    private String fileId;

    private String failureCode;

    private int attempts;

    private StudyAudio(Long chapterId, AudioVariant variant) {
        if (chapterId == null || chapterId <= 0 || variant == null) {
            throw new InvalidStudyAudioException("챕터 ID 또는 음성 버전");
        }
        this.chapterId = chapterId;
        this.variant = variant;
        this.fileId = UUID.randomUUID().toString();
        this.status = AudioStatus.PENDING;
        this.stage = GenerationStage.SCRIPT;
    }

    public static StudyAudio create(StudyAudioCreateRequest request) {
        if (request == null) {
            throw new InvalidStudyAudioException("음성 생성 정보");
        }

        return new StudyAudio(request.chapterId(), request.variant());
    }

    public boolean tryBegin() {
        if (status == AudioStatus.READY || status == AudioStatus.GENERATING) {
            return false;
        }
        begin();
        return true;
    }

    public void begin() {
        if (status != AudioStatus.PENDING && status != AudioStatus.FAILED && status != AudioStatus.INTERRUPTED) {
            throw new InvalidStudyStatusTransitionException();
        }
        status = AudioStatus.GENERATING;
        failureCode = null;
        attempts++;
    }

    public void recordScript(String script) {
        requireStage(GenerationStage.SCRIPT);
        requireText(script, "설명 대본");
        this.script = script.strip();
        stage = GenerationStage.REVIEW;
    }

    public void approveScript(String script) {
        requireStage(GenerationStage.REVIEW);
        requireText(script, "설명 대본");
        this.script = script.strip();
        stage = GenerationStage.AUDIO;
    }

    public void markStoragePending() {
        requireStage(GenerationStage.AUDIO);
        stage = GenerationStage.STORAGE;
    }

    public void complete(String storageKey) {
        requireRunning();
        if (stage != GenerationStage.AUDIO && stage != GenerationStage.STORAGE) {
            throw new InvalidStudyStatusTransitionException();
        }

        requireText(storageKey, "음성 파일 저장 키");
        this.storageKey = storageKey;
        stage = GenerationStage.COMPLETE;
        status = AudioStatus.READY;
        failureCode = null;
    }

    public void fail(String code) {
        requireRunning();
        requireText(code, "실패 코드");
        failureCode = code;
        status = AudioStatus.FAILED;
    }

    public void interrupt() {
        if (status == AudioStatus.GENERATING) {
            status = AudioStatus.INTERRUPTED;
            failureCode = "GENERATION_INTERRUPTED";
        }
    }

    private void requireStage(GenerationStage expected) {
        requireRunning();
        if (stage != expected) {
            throw new InvalidStudyStatusTransitionException();
        }
    }

    private void requireRunning() {
        if (status != AudioStatus.GENERATING) {
            throw new InvalidStudyStatusTransitionException();
        }
    }

    private static void requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new InvalidStudyAudioException(field);
        }
    }
}
