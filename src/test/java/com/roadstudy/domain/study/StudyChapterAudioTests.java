package com.roadstudy.domain.study;

import com.roadstudy.domain.study.request.StudyChapterCreateRequest;
import com.roadstudy.domain.study.request.StudyAudioCreateRequest;
import com.roadstudy.domain.study.enums.GenerationStage;
import com.roadstudy.domain.study.enums.AudioVariant;
import com.roadstudy.domain.study.enums.AudioStatus;
import com.roadstudy.domain.study.exception.InvalidStudyAudioException;
import com.roadstudy.domain.study.exception.InvalidStudyChapterException;
import com.roadstudy.domain.study.exception.InvalidStudyStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("학습 챕터와 음성 도메인")
class StudyChapterAudioTests {
    @Test
    void preservesReviewedScriptWhenAudioFailsAndRetriesOnlyAudio() {
        StudyAudio audio = StudyAudio.create(new StudyAudioCreateRequest(7L, AudioVariant.BASIC));
        audio.begin();
        audio.recordScript("초안");
        audio.approveScript("검토한 대본");
        audio.fail("TTS_TIMEOUT");
        audio.begin();
        assertThat(audio.getScript()).isEqualTo("검토한 대본");
        assertThat(audio.getStage()).isEqualTo(GenerationStage.AUDIO);
        audio.complete("7/basic.mp3");
        assertThat(audio.getStatus()).isEqualTo(AudioStatus.READY);
        assertThatThrownBy(audio::begin).isInstanceOf(InvalidStudyStatusTransitionException.class);
    }

    @Test
    void detailedFailureDoesNotChangeBasicAudio() {
        StudyAudio basic = readyAudio();
        StudyAudio detailed = StudyAudio.create(new StudyAudioCreateRequest(7L, AudioVariant.DETAILED));
        detailed.begin();
        detailed.fail("GENERATION_TIMEOUT");
        assertThat(basic.getStatus()).isEqualTo(AudioStatus.READY);
        assertThat(basic.getStorageKey()).isEqualTo("7/basic.mp3");
        assertThat(basic.getFileId()).isNotEqualTo(detailed.getFileId());
    }

    @Test
    void restartInterruptsOnlyRunningWorkAndRequiresManualBegin() {
        StudyAudio audio = StudyAudio.create(new StudyAudioCreateRequest(7L, AudioVariant.BASIC));
        audio.begin();
        audio.recordScript("초안");
        audio.interrupt();
        assertThat(audio.getStatus()).isEqualTo(AudioStatus.INTERRUPTED);
        assertThat(audio.getStage()).isEqualTo(GenerationStage.REVIEW);
        audio.begin();
        assertThat(audio.getScript()).isEqualTo("초안");
        assertThatThrownBy(audio::begin).isInstanceOf(InvalidStudyStatusTransitionException.class);
    }

    @Test
    void rejectsCompletingUnreviewedScriptAndOverwritingReadyAudio() {
        StudyAudio audio = StudyAudio.create(new StudyAudioCreateRequest(7L, AudioVariant.BASIC));
        audio.begin();
        audio.recordScript("초안");
        assertThatThrownBy(() -> audio.complete("audio.mp3")).isInstanceOf(InvalidStudyStatusTransitionException.class);
        StudyAudio ready = readyAudio();
        assertThatThrownBy(() -> ready.fail("FAIL")).isInstanceOf(InvalidStudyStatusTransitionException.class);
        assertThatThrownBy(() -> ready.approveScript("변경")).isInstanceOf(InvalidStudyStatusTransitionException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void rejectsBlankScript(String script) {
        StudyAudio audio = StudyAudio.create(new StudyAudioCreateRequest(7L, AudioVariant.BASIC));
        audio.begin();
        assertThatThrownBy(() -> audio.recordScript(script)).isInstanceOf(InvalidStudyAudioException.class);
    }

    @Test
    void rejectsInvalidChapterAndAudioIdentity() {
        assertThatThrownBy(() -> StudyAudio.create(null)).isInstanceOf(InvalidStudyAudioException.class);
        assertThatThrownBy(() -> StudyChapter.create(null))
                .isInstanceOf(InvalidStudyChapterException.class);
        assertThatThrownBy(() -> StudyChapter.create(new StudyChapterCreateRequest(null, 1, "제목", "목표", "근거")))
                .isInstanceOf(InvalidStudyChapterException.class);
        assertThatThrownBy(() -> StudyChapter.create(new StudyChapterCreateRequest(1L, 0, "제목", "목표", "근거")))
                .isInstanceOf(InvalidStudyChapterException.class);
        assertThatThrownBy(() -> StudyAudio.create(new StudyAudioCreateRequest(0L, AudioVariant.BASIC)))
                .isInstanceOf(InvalidStudyAudioException.class);
        assertThatThrownBy(() -> StudyAudio.create(new StudyAudioCreateRequest(1L, null))).isInstanceOf(InvalidStudyAudioException.class);
    }

    private StudyAudio readyAudio() {
        StudyAudio audio = StudyAudio.create(new StudyAudioCreateRequest(7L, AudioVariant.BASIC));
        audio.begin();
        audio.recordScript("초안");
        audio.approveScript("검토한 대본");
        audio.complete("7/basic.mp3");
        return audio;
    }
}
