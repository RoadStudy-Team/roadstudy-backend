package com.roadstudy.application.study.exception;

import com.roadstudy.global.exception.ApplicationException;
import com.roadstudy.global.exception.FailureType;

public class PdfExtractionException extends ApplicationException {
    private PdfExtractionException(String code, String message, FailureType type, Throwable cause) {
        super(code, message, type, cause);
    }

    private PdfExtractionException(String code, String message, FailureType type) {
        super(code, message, type);
    }

    public static PdfExtractionException emptyFile() {
        return new PdfExtractionException("EMPTY_FILE", "업로드한 파일이 비어 있습니다.", FailureType.INVALID_INPUT);
    }

    public static PdfExtractionException invalidPdf(Throwable cause) {
        return new PdfExtractionException("INVALID_PDF", "올바른 PDF 파일을 업로드해 주세요.", FailureType.INVALID_INPUT, cause);
    }

    public static PdfExtractionException fileTooLarge() {
        return new PdfExtractionException("FILE_TOO_LARGE", "PDF 파일은 최대 50MB까지 업로드할 수 있습니다.",
                FailureType.PAYLOAD_TOO_LARGE);
    }

    public static PdfExtractionException emptyText() {
        return new PdfExtractionException("PDF_TEXT_EMPTY", "PDF에서 추출할 수 있는 텍스트가 없습니다.",
                FailureType.UNPROCESSABLE_CONTENT);
    }

    public static PdfExtractionException extractionFailed(Throwable cause) {
        return new PdfExtractionException("PDF_EXTRACTION_FAILED", "PDF의 텍스트를 추출할 수 없습니다.",
                FailureType.UNPROCESSABLE_CONTENT, cause);
    }
}
