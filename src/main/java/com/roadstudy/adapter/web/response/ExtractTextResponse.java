package com.roadstudy.adapter.web.response;

import com.roadstudy.application.study.ExtractedStudyText;

public record ExtractTextResponse(String fileName, int pageCount, int characterCount, String text) {

    public static ExtractTextResponse from(String fileName, ExtractedStudyText extracted) {
        return new ExtractTextResponse(fileName, extracted.pageCount(), extracted.text().length(), extracted.text());
    }
}
