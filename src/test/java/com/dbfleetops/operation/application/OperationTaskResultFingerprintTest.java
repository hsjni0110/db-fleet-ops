package com.dbfleetops.operation.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationTaskResultFingerprintTest {
    private final OperationTaskResultFingerprint fingerprint = new OperationTaskResultFingerprint();

    @Test
    void fingerprintsAreStableAndSeparateSuccessFromFailure() {
        assertThat(fingerprint.success("{}"))
                .isEqualTo(fingerprint.success("{}"))
                .hasSize(64)
                .isNotEqualTo(fingerprint.failure("ERROR", "{}"));
        assertThat(fingerprint.failure("ERROR", "one"))
                .isNotEqualTo(fingerprint.failure("ERROR", "two"));
    }
}
