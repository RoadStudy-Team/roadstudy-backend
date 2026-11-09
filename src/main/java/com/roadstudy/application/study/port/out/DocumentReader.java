package com.roadstudy.application.study.port.out;

import com.roadstudy.application.study.ExtractedStudyText;

public interface DocumentReader {
    ExtractedStudyText read(byte[] content);
}
