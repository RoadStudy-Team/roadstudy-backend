package com.roadstudy.adapter.web;

import com.roadstudy.adapter.web.docs.StudyControllerDocs;
import com.roadstudy.adapter.web.request.GenerateAudioRequest;
import com.roadstudy.adapter.web.response.*;
import com.roadstudy.application.study.model.ExtractedStudyText;
import com.roadstudy.application.study.model.StudyAudio;
import com.roadstudy.application.study.port.in.FindStudy;
import com.roadstudy.application.study.port.in.FindStudyAudio;
import com.roadstudy.application.study.port.in.GenerateStudy;
import com.roadstudy.application.study.port.in.StudyGenerationSteps;
import com.roadstudy.domain.study.StudySession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studies")
public class StudyController implements StudyControllerDocs {
    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final StudyGenerationSteps studyGenerationSteps;
    private final FindStudyAudio findStudyAudio;
    private final GenerateStudy generateStudy;
    private final FindStudy findStudy;

    @Override
    @GetMapping
    public ResponseEntity<List<StudyListItemResponse>> listStudies() {
        return ResponseEntity.ok(findStudy.findAll().stream().map(StudyListItemResponse::from).toList());
    }

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudyResponse> startGeneration(@RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            StudySession session = generateStudy.generateStudyAsync(file.getOriginalFilename(), input);
            return ResponseEntity.accepted()
                    .location(URI.create("/api/studies/" + session.getId()))
                    .body(StudyResponse.from(session));
        }
    }

    @Override
    @GetMapping("/{studyId}")
    public ResponseEntity<StudyResponse> getStudy(@PathVariable("studyId") Long studyId) {
        return ResponseEntity.ok(StudyResponse.from(findStudy.findById(studyId)));
    }

    @Override
    @PostMapping(path = "/generate-explanation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudyExplanationResponse> generateExplanation(@RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            StudySession session = studyGenerationSteps.generateExplanation(file.getOriginalFilename(), input);
            return ResponseEntity.ok(StudyExplanationResponse.from(session));
        }
    }

    @Override
    @PostMapping(path = "/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExtractTextResponse> extractText(@RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            ExtractedStudyText extractText = studyGenerationSteps.extractText(input);
            return ResponseEntity.ok(ExtractTextResponse.from(file.getOriginalFilename(), extractText));
        }
    }

    @Override
    @PostMapping(path = "/generate-audio", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudyAudioResponse> generateAudio(@RequestBody GenerateAudioRequest request) {
        StudyAudio audio = studyGenerationSteps.generateAudio(request.explanationScript());
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
