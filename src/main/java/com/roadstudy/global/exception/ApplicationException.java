package com.roadstudy.global.exception;

public abstract class ApplicationException extends RuntimeException {
    private final String code;
    private final FailureType failureType;

    protected ApplicationException(String code, String message, FailureType failureType) {
        this(code, message, failureType, null);
    }

    protected ApplicationException(String code, String message, FailureType failureType, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.failureType = failureType;
    }

    public String getCode() {
        return code;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}
