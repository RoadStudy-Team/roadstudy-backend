package com.roadstudy.adapter.web.docs;

import java.io.IOException;

import com.roadstudy.adapter.web.request.GenerateAudioRequest;
import com.roadstudy.adapter.web.response.StudyAudioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public interface StudyAudioApiDocs {
    @Operation(summary = "학습 음성 생성 (임시)",
            description = "설명 대본을 한국어 MP3로 변환해 저장합니다. 대본은 UTF-8 기준 5,000바이트(한글 약 1,600자)까지 허용합니다.")
    @ApiResponse(responseCode = "200", description = "음성 생성 성공",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StudyAudioResponse.class)))
    @ApiResponse(responseCode = "400", description = "대본이 없거나 요청 본문이 잘못됨",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "음성 합성 입력 길이 초과",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "음성 파일 저장 실패",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "음성 합성 서비스 호출 실패",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "504", description = "음성 합성 서비스 응답 시간 초과",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<StudyAudioResponse> generateAudio(GenerateAudioRequest request);

    @Operation(summary = "학습 음성 조회 (임시)", description = "생성 API가 반환한 audioId로 저장된 MP3를 내려받거나 재생합니다.")
    @ApiResponse(responseCode = "200", description = "MP3 반환",
            content = @Content(mediaType = "audio/mpeg", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "음성 파일 없음",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "206", description = "MP3 구간 반환",
            content = @Content(mediaType = "audio/mpeg", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "416", description = "음성 구간 요청 오류",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<Resource> getAudio(String audioId, String rangeHeader) throws IOException;
}
