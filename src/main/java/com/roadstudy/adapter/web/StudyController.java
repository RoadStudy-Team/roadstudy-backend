package com.roadstudy.adapter.web;

import java.io.IOException;

import com.roadstudy.adapter.web.docs.StudyTextApiDocs;
import com.roadstudy.adapter.web.response.ExtractTextResponse;
import com.roadstudy.application.study.port.in.ExtractStudyText;
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
public class StudyController implements StudyTextApiDocs {
    private final ExtractStudyText extractStudyText;

    @Override
    @PostMapping(path = "/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExtractTextResponse> extractText(@RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return ResponseEntity.ok(ExtractTextResponse.from(file.getOriginalFilename(), extractStudyText.extract(input)));
        }
    }
}
