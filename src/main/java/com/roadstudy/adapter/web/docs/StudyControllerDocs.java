package com.roadstudy.adapter.web.docs;

import java.io.IOException;
import java.util.List;

import com.roadstudy.adapter.web.response.ExtractTextResponse;
import com.roadstudy.adapter.web.response.StudyExplanationResponse;
import com.roadstudy.adapter.web.response.StudyListItemResponse;
import com.roadstudy.adapter.web.response.StudyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface StudyControllerDocs {
    @Operation(summary = "학습 세션 목록 조회", description = "등록된 학습 세션을 최신 등록순으로 반환합니다.")
    @ApiResponse(responseCode = "200", description = "목록 조회 성공. 등록된 세션이 없으면 빈 배열을 반환합니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = StudyListItemResponse.class))))
    ResponseEntity<List<StudyListItemResponse>> listStudies();

    @Operation(summary = "PDF 텍스트 추출", description = "최대 50MB의 PDF를 업로드해 전체 페이지의 텍스트를 추출합니다.")
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

    @Operation(summary = "학습 설명 대본 생성", description = "최대 50MB의 PDF에서 약 5분 분량의 한국어 학습 설명 대본을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "대본 생성 및 저장 성공. 학습 세션 ID를 반환합니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StudyExplanationResponse.class)))
    @ApiResponse(responseCode = "400", description = "빈 파일 또는 잘못된 PDF",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "업로드 용량 초과",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "텍스트를 추출할 수 없거나 생성 입력 길이 초과",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "외부 서비스 호출 실패 또는 잘못된 생성 결과",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "504", description = "외부 서비스 응답 시간 초과",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<StudyExplanationResponse> generateExplanation(MultipartFile file) throws IOException;

    @Operation(summary = "학습 자동 생성 시작",
            description = "최대 50MB의 PDF를 업로드하면 텍스트 추출과 입력 검증을 마친 뒤 바로 응답하고, "
                    + "대본 생성과 음성 생성은 백그라운드에서 이어서 처리합니다. 진행 상태는 학습 세션 조회로 확인합니다.")
    @ApiResponse(responseCode = "202", description = "생성 요청 접수. 처리 중인 학습 세션과 조회 주소(Location)를 반환합니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StudyResponse.class)))
    @ApiResponse(responseCode = "400", description = "빈 파일 또는 잘못된 PDF",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "업로드 용량 초과",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "텍스트를 추출할 수 없거나 생성 입력 길이 초과",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<StudyResponse> startGeneration(MultipartFile file) throws IOException;

    @Operation(summary = "학습 세션 조회",
            description = "학습 세션의 처리 상태와 결과를 조회합니다. 상태는 PROCESSING, COMPLETED, FAILED 중 하나이며 "
                    + "COMPLETED일 때 audioUrl로 MP3를 재생할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "학습 세션 조회 성공",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StudyResponse.class)))
    @ApiResponse(responseCode = "404", description = "학습 세션 없음",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<StudyResponse> getStudy(Long studyId);
}
