package com.roadstudy.application.study.port.out;

import com.roadstudy.application.study.model.StudyDocument;

public interface DocumentReader {
    StudyDocument read(byte[] content);
}
