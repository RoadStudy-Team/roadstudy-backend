package com.roadstudy.application.study.port.out;

public interface SpeechSynthesizer {
    byte[] synthesize(String script);
}
