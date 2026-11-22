package com.roadstudy.adapter.web;

import com.roadstudy.adapter.web.docs.StudyControllerDocs;
import com.roadstudy.adapter.web.response.StudySessionResponse;
import com.roadstudy.application.study.port.in.FindStudy;
import com.roadstudy.application.study.port.in.GenerateStudy;
import com.roadstudy.application.study.exception.StudyAudioException;
import com.roadstudy.domain.study.StudySession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studies")
public class StudyController implements StudyControllerDocs {
    private final GenerateStudy generateStudy;
    private final FindStudy findStudy;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudySessionResponse> generateStudyAsync(@RequestPart("file") MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            StudySession session = generateStudy.generateStudyAsync(file.getOriginalFilename(), input);

            return ResponseEntity.accepted().location(URI.create("/api/studies/" + session.getId()))
                    .body(StudySessionResponse.from(findStudy.findById(session.getId())));
        }
    }

    @Override
    @GetMapping
    public ResponseEntity<List<StudySessionResponse>> listStudies() {
        return ResponseEntity.ok(findStudy.findAll().stream().map(StudySessionResponse::from).toList());
    }

    @Override
    @GetMapping("/{sessionId}")
    public ResponseEntity<StudySessionResponse> getStudy(@PathVariable Long sessionId) {
        return ResponseEntity.ok(StudySessionResponse.from(findStudy.findById(sessionId)));
    }

    @Override
    @PostMapping("/{sessionId}/retry")
    public ResponseEntity<StudySessionResponse> retryGeneration(@PathVariable Long sessionId) {
        generateStudy.retryGeneration(sessionId);
        return ResponseEntity.accepted().body(StudySessionResponse.from(findStudy.findById(sessionId)));
    }

    @Override
    @PostMapping("/{sessionId}/chapters/{chapterId}/details")
    public ResponseEntity<StudySessionResponse> generateDetailedAudio(@PathVariable Long sessionId, @PathVariable Long chapterId) {
        generateStudy.generateDetailedAudioAsync(sessionId, chapterId);
        return ResponseEntity.accepted().body(StudySessionResponse.from(findStudy.findById(sessionId)));
    }

    @Override
    @PostMapping("/{sessionId}/chapters/{chapterId}/audios/{audioId}/retry")
    public ResponseEntity<StudySessionResponse> retryAudioGeneration(@PathVariable Long sessionId, @PathVariable Long chapterId, @PathVariable Long audioId) {
        generateStudy.retryAudioGeneration(sessionId, chapterId, audioId);
        return ResponseEntity.accepted().body(StudySessionResponse.from(findStudy.findById(sessionId)));
    }

    @Override
    @GetMapping("/audio/{audioId}")
    public ResponseEntity<Resource> getAudio(@PathVariable Long audioId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        Resource resource = new FileSystemResource(findStudy.findAudio(audioId));
        validateRange(range, resource);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(resource);
    }

    private static void validateRange(String range, Resource resource) {
        if (range == null)
            return;

        long length;
        try {
            length = resource.contentLength();
        } catch (IOException ex) {
            throw StudyAudioException.readFailed(ex);
        }

        try {
            HttpRange.toResourceRegions(HttpRange.parseRanges(range), resource);
        } catch (IllegalArgumentException ex) {
            ErrorResponseException failure = new ErrorResponseException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            failure.getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
            throw failure;
        }
    }
}
