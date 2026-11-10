package com.roadstudy.adapter.web.docs;

import java.io.IOException;

import com.roadstudy.adapter.web.response.ExtractTextResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface StudyTextApiDocs {
    @Operation(summary = "PDF 텍스트 추출", description = "최대 10MB의 PDF를 업로드해 전체 페이지의 텍스트를 추출합니다.")
    @ApiResponse(responseCode = "200", description = "텍스트 추출 성공",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ExtractTextResponse.class)))
    @ApiResponse(responseCode = "400", description = "빈 파일 또는 잘못된 PDF",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "업로드 용량 초과",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "텍스트가 없거나 추출할 수 없는 PDF",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<ExtractTextResponse> extractText(MultipartFile file) throws IOException;
}
