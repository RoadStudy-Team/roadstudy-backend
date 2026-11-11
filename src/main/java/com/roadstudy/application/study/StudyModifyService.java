package com.roadstudy.application.study;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.roadstudy.application.study.exception.PdfExtractionException;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.application.study.port.in.ExtractStudyText;
import com.roadstudy.application.study.port.in.GenerateStudyAudio;
import com.roadstudy.application.study.port.out.AudioStorage;
import com.roadstudy.application.study.port.out.DocumentReader;
import com.roadstudy.application.study.port.out.SpeechSynthesizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyModifyService implements ExtractStudyText, GenerateStudyAudio {
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024;
    // 음성 합성 한 번에 보낼 수 있는 대본 크기. 글자 수가 아니라 UTF-8 바이트 기준이다.
    private static final int MAX_SCRIPT_BYTES = 5_000;

    private final DocumentReader documentReader;
    private final SpeechSynthesizer speechSynthesizer;
    private final AudioStorage audioStorage;

    @Override
    public StudyAudio generate(String explanationScript) {
        if (explanationScript == null || explanationScript.isBlank()) {
            throw StudyAudioException.emptyScript();
        }

        String script = explanationScript.strip();
        if (script.getBytes(StandardCharsets.UTF_8).length > MAX_SCRIPT_BYTES) {
            throw StudyAudioException.scriptTooLong(MAX_SCRIPT_BYTES);
        }

        byte[] audio = speechSynthesizer.synthesize(script);
        return audioStorage.save(UUID.randomUUID(), audio);
    }

    @Override
    public ExtractedStudyText extract(InputStream input) throws IOException {
        byte[] content = input.readNBytes(MAX_FILE_BYTES + 1);

        if (content.length == 0) {
            throw PdfExtractionException.emptyFile();
        }

        if (content.length > MAX_FILE_BYTES) {
            throw PdfExtractionException.fileTooLarge();
        }

        var document = documentReader.read(content);
        String text = document.text().strip();
        if (text.isBlank()) {
            throw PdfExtractionException.emptyText();
        }

        return new ExtractedStudyText(document.pageCount(), text);
    }
}
