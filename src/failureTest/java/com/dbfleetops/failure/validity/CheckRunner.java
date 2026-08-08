package com.dbfleetops.failure.validity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 실험의 공통 실행 순서, 터미널 출력과 최종 판정을 담당합니다. */
final class CheckRunner {
    void verify(ArchitectureCheck check) throws Exception {
        printIntroduction(check);

        Map<String, Object> measurements = check.measure();
        boolean supported = check.supports(measurements);

        printResult(check, measurements, supported);
        assertTrue(supported,
                "구조의 장점이 미리 정한 기준을 만족하지 못했습니다.");
    }

    private void printIntroduction(ArchitectureCheck check) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("[구조 확인] " + check.title());
        System.out.println("[확인할 내용] " + check.claim());
        System.out.println("[통과 기준] " + check.criterion());
        System.out.println("============================================================");
    }

    private void printResult(ArchitectureCheck check, Map<String, Object> measurements,
            boolean supported) {
        System.out.println("[관찰 결과]");
        measurements.forEach(
                (name, value) -> System.out.println("  - " + name + ": " + value));
        System.out.println("[판정] " + (supported ? "SUPPORTED" : "NOT_SUPPORTED"));
        System.out.println("[한계] " + check.limitation());
    }
}
