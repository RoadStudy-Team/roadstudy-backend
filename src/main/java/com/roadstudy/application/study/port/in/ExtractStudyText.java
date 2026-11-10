package com.roadstudy.application.study.port.in;

import java.io.IOException;
import java.io.InputStream;

import com.roadstudy.application.study.ExtractedStudyText;

public interface ExtractStudyText {
    ExtractedStudyText extract(InputStream input) throws IOException;
}
