package com.roadstudy.adapter.web;

import java.io.IOException;

import com.roadstudy.adapter.web.docs.StudyAudioApiDocs;
import com.roadstudy.adapter.web.docs.StudyControllerDocs;
import com.roadstudy.adapter.web.request.GenerateAudioRequest;
import com.roadstudy.adapter.web.response.ExtractTextResponse;
import com.roadstudy.adapter.web.response.StudyAudioResponse;
import com.roadstudy.adapter.web.response.StudyExplanationResponse;
import com.roadstudy.application.study.port.in.ExtractStudyText;
import com.roadstudy.application.study.port.in.FindStudyAudio;
import com.roadstudy.application.study.port.in.GenerateStudyAudio;
import com.roadstudy.application.study.port.in.GenerateStudyExplanation;
import com.roadstudy.application.study.StudyAudio;
import com.roadstudy.domain.study.StudySession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studies")
public class StudyController implements StudyControllerDocs, StudyAudioApiDocs {
    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final ExtractStudyText extractStudyText;
    private final GenerateStudyExplanation generateStudyExplanation;
    private final GenerateStudyAudio generateStudyAudio;
    private final FindStudyAudio findStudyAudio;

    @Override
    @PostMapping(path = "/generate-explanation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudyExplanationResponse> generateExplanation(@RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            StudySession session = generateStudyExplanation.generate(file.getOriginalFilename(), input);
            return ResponseEntity.ok(StudyExplanationResponse.from(session));
        }
    }

    @Override
    @PostMapping(path = "/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExtractTextResponse> extractText(@RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return ResponseEntity.ok(ExtractTextResponse.from(file.getOriginalFilename(), extractStudyText.extract(input)));
        }
    }

    @Override
    @PostMapping(path = "/generate-audio", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudyAudioResponse> generateAudio(@RequestBody GenerateAudioRequest request) {
        StudyAudio audio = generateStudyAudio.generate(request.explanationScript());
        return ResponseEntity.ok(StudyAudioResponse.from(audio));
    }

    @Override
    @GetMapping("/audio/{audioId}")
    public ResponseEntity<Resource> getAudio(
            @PathVariable("audioId") String audioId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {
        var audio = findStudyAudio.find(audioId);
        var resource = new FileSystemResource(audio.path());
        validateRange(rangeHeader, resource);
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .body(resource);
    }

    private static void validateRange(String rangeHeader, Resource resource) throws IOException {
        if (rangeHeader == null) {
            return;
        }
        long contentLength = resource.contentLength();
        try {
            HttpRange.toResourceRegions(HttpRange.parseRanges(rangeHeader), resource);
        } catch (IllegalArgumentException ex) {
            var failure = new ErrorResponseException(
                    HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            failure.getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength);
            throw failure;
        }
    }
}
