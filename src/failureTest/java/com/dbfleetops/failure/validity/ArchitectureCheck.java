package com.dbfleetops.failure.validity;

import java.util.Map;

/** 하나의 구조적 장점을 측정하고 판정합니다. */
interface ArchitectureCheck {
    String title();
    String claim();
    String criterion();
    Map<String, Object> measure() throws Exception;
    boolean supports(Map<String, Object> measurements);
    String limitation();
}
