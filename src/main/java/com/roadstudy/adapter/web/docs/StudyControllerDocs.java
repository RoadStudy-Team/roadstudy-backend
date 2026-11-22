package com.roadstudy.adapter.web.docs;

import com.roadstudy.adapter.web.response.StudySessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StudyControllerDocs {
    @Operation(summary = "챕터형 학습 생성", description = "PDF 한 개를 보관하고 전체 분석·강의 계획·기본 챕터를 순서대로 생성합니다. 완료된 챕터부터 재생 가능합니다.")
    @ApiResponse(responseCode = "202", description = "접수 완료. Location에서 진행 상태 조회")
    @ApiResponse(responseCode = "400", description = "빈 파일 또는 잘못된 PDF")
    @ApiResponse(responseCode = "413", description = "파일 용량 초과")
    @ApiResponse(responseCode = "422", description = "읽을 수 있는 학습 내용 없음")
    ResponseEntity<StudySessionResponse> generateStudyAsync(MultipartFile file);

    @Operation(summary = "학습 목록 조회")
    ResponseEntity<List<StudySessionResponse>> listStudies();

    @Operation(summary = "강의·챕터·기본 및 상세 버전 조회", description = "기본 버전만 챕터 순서대로 전체 재생합니다. 미완성 챕터는 안내 후 건너뛰며 상세 버전은 사용자가 별도로 선택합니다.")
    @ApiResponse(responseCode = "404", description = "세션 없음")
    ResponseEntity<StudySessionResponse> getStudy(Long sessionId);

    @Operation(summary = "PDF 업로드 후 전체 기본 학습 생성 재시도", description = """
            - 사용 시점: PDF 업로드로 접수된 세션의 기본 학습 생성이 실패하거나 중단된 경우
            - 처리 범위: 학습 계획 생성부터 각 챕터의 기본 대본·음성 생성까지 미완료 작업 재시도
            - PDF 재업로드 불필요. 저장된 자료·계획·대본을 재사용하고 완료된 기본 오디오는 유지
            - 상세 버전은 대상에서 제외. 특정 기본·상세 오디오만 재시도하려면 오디오 단위 재시도 API 사용
            - 이미 전체 생성 작업이 실행 중이면 추가 작업 없이 현재 상태 반환
            - 비동기 접수 후 GET /api/studies/{sessionId}로 진행 상태 확인
            """)
    @ApiResponse(responseCode = "202", description = "재시도 요청 접수. 응답은 현재 세션 상태이며 생성 완료를 의미하지 않음")
    ResponseEntity<StudySessionResponse> retryGeneration(Long sessionId);

    @Operation(summary = "챕터 더 자세히 듣기 생성", description = "학습 목표와 난이도를 유지하며 단계·이유·예시를 보강합니다. 챕터당 한 버전만 보관하고 완료 결과를 재사용합니다.")
    ResponseEntity<StudySessionResponse> generateDetailedAudio(Long sessionId, Long chapterId);

    @Operation(summary = "특정 챕터의 기본·상세 오디오 하나 재시도", description = """
            - 사용 시점: 특정 챕터의 기본 또는 상세 오디오 생성이 실패하거나 중단된 경우
            - 처리 범위: audioId로 지정한 오디오 하나의 미완료 단계만 재시도
            - sessionId·chapterId·audioId는 세션 조회 응답에서 확인. 해당 세션과 챕터에 속한 오디오만 요청 가능
            - 예: 대본 검토 후 TTS가 실패했다면 저장된 대본으로 음성 생성부터 재시도
            - 다른 챕터와 다른 버전은 그대로 유지. 지정한 오디오가 이미 생성 중이거나 완료된 경우 추가 생성 없음
            - 상세 오디오를 처음 만들려면 챕터 더 자세히 듣기 생성 API 사용
            - 비동기 접수 후 GET /api/studies/{sessionId}로 해당 오디오 상태 확인
            """)
    @ApiResponse(responseCode = "202", description = "재시도 요청 접수. 응답은 현재 세션 상태이며 생성 완료를 의미하지 않음")
    ResponseEntity<StudySessionResponse> retryAudioGeneration(Long sessionId, Long chapterId, Long audioId);

    @Operation(summary = "완성된 챕터 음성 재생")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "audio/mpeg", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "206", content = @Content(mediaType = "audio/mpeg", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "416", description = "잘못된 재생 구간")
    @ApiResponse(responseCode = "404", description = "준비된 음성 없음")
    ResponseEntity<Resource> getAudio(Long audioId, String range);
}
