package com.roadstudy.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
            response = problem(status, "UPLOAD_TOO_LARGE", "Upload exceeds the allowed size.");
        } else if (status.is4xxClientError() && (ex instanceof MethodArgumentNotValidException
                || ex instanceof HandlerMethodValidationException)) {
            response = problem(status, "VALIDATION_FAILED", "Request validation failed.");
        } else if (status.value() == 500) {
            log.error("Unhandled request failure of type {}", ex.getClass().getName());
            response = problem(status, "INTERNAL_ERROR", "An unexpected error occurred.");
        } else {
            response = problem(status, "HTTP_" + status.value(), "The request could not be processed.");
        }
        return super.handleExceptionInternal(ex, response, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
