package com.roadstudy.application.study.port.out;

public interface SpeechSynthesizer {
    // 설명 대본을 MP3 음성 데이터로 변환하며 파일 저장은 별도 처리
    byte[] synthesize(String script);
}
