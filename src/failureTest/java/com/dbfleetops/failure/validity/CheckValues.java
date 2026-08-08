package com.dbfleetops.failure.validity;

import java.util.LinkedHashMap;
import java.util.Map;

/** 실험 결과를 문서에 적은 순서대로 만들기 위한 작은 도우미입니다. */
final class CheckValues {
    private CheckValues() {
    }

    static Map<String, Object> inOrder(Object... namesAndValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            result.put(namesAndValues[index].toString(), namesAndValues[index + 1]);
        }
        return result;
    }

    static double mebibytes(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0 * 100.0) / 100.0;
    }
}
