package com.roadstudy.adapter.web;

import java.io.IOException;

import com.roadstudy.adapter.web.docs.StudyControllerDocs;
import com.roadstudy.adapter.web.response.ExtractTextResponse;
import com.roadstudy.adapter.web.response.StudyExplanationResponse;
import com.roadstudy.application.study.port.in.ExtractStudyText;
import com.roadstudy.application.study.port.in.GenerateStudyExplanation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studies")
public class StudyController implements StudyControllerDocs {
    private final ExtractStudyText extractStudyText;
    private final GenerateStudyExplanation generateStudyExplanation;

    @Override
    @PostMapping(path = "/generate-explanation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudyExplanationResponse> generateExplanation(@RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return ResponseEntity.ok(StudyExplanationResponse.from(generateStudyExplanation.generate(input)));
        }
    }

    @Override
    @PostMapping(path = "/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExtractTextResponse> extractText(@RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return ResponseEntity.ok(ExtractTextResponse.from(file.getOriginalFilename(), extractStudyText.extract(input)));
        }
    }
}
