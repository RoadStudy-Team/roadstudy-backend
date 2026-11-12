package com.roadstudy.adapter.tts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TtsScriptChunks {
    private static final int MAX_REQUEST_BYTES = 5_000;

    // 요청 한도 내 대본 분할 (문장 끝 우선)
    static List<String> split(String script) {
        if (script.getBytes(StandardCharsets.UTF_8).length <= MAX_REQUEST_BYTES) {
            return List.of(script);
        }

        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.KOREAN);
        sentences.setText(script);

        ArrayList<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < script.length()) {
            int maxEnd = endWithinLimit(script, start);
            int end = maxEnd == script.length() || sentences.isBoundary(maxEnd)
                    ? maxEnd : sentences.preceding(maxEnd);
            if (end <= start) {
                end = maxEnd;
            }

            String chunk = script.substring(start, end);
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            start = end;
        }
        return chunks;
    }

    // 시작 위치부터 바이트 한도 내 최대 종료 인덱스 탐색
    private static int endWithinLimit(String script, int start) {
        int end = start;
        int bytes = 0;
        while (end < script.length()) {
            int codePoint = script.codePointAt(end);
            bytes += utf8Size(codePoint);
            if (bytes > MAX_REQUEST_BYTES) {
                break;
            }
            end += Character.charCount(codePoint);
        }
        return end;
    }

    // 코드 포인트의 UTF-8 바이트 수 계산
    private static int utf8Size(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        return Character.isBmpCodePoint(codePoint) ? 3 : 4;
    }
}
