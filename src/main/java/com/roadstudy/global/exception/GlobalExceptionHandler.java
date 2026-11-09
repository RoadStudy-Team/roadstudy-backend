package com.roadstudy.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail handleApplicationException(ApplicationException ex) {
        HttpStatus status = switch (ex.getFailureType()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE_CONTENT -> HttpStatus.UNPROCESSABLE_ENTITY;
            case EXTERNAL_SERVICE_FAILURE -> HttpStatus.BAD_GATEWAY;
            case EXTERNAL_SERVICE_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
        };
        return problem(status, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        if (ex instanceof ErrorResponse error) {
            return handleExceptionInternal(ex, null, error.getHeaders(), error.getStatusCode(), request);
        }
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail response;
        if (ex instanceof MaxUploadSizeExceededException) {
            response = problem(status, "UPLOAD_TOO_LARGE", "업로드 파일이 허용 용량을 초과했습니다.");
        } else if (status.is4xxClientError() && (ex instanceof MethodArgumentNotValidException
                || ex instanceof HandlerMethodValidationException)) {
            response = problem(status, "VALIDATION_FAILED", "요청의 입력값이 올바르지 않습니다.");
        } else if (status.value() == 500) {
            log.error("요청 처리 중 예상하지 못한 예외가 발생했습니다. 예외 유형 {}", ex.getClass().getName());
            response = problem(status, "INTERNAL_ERROR", "서버에서 예상하지 못한 오류가 발생했습니다.");
        } else {
            response = problem(status, "HTTP_" + status.value(), "요청을 처리할 수 없습니다.");
        }
        return super.handleExceptionInternal(ex, response, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
