package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.required.ConfigurationApplyOutcome;
import com.dbfleetops.operation.application.required.ConfigurationCheckOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerResultMessageFactoryTest {

    private final WorkerResultMessageFactory messages = new WorkerResultMessageFactory();

    @Test
    void createsConfigurationCheckMessage() {
        String message = messages.configurationCheck(
                new ConfigurationCheckOutcome(3L, "COMPLIANT"));

        assertThat(message).isEqualTo(
                "Configuration check completed. driftId=3, status=COMPLIANT");
    }

    @Test
    void createsConfigurationApplyMessage() {
        String message = messages.configurationApply(
                new ConfigurationApplyOutcome(7L, true, "SUCCEEDED", 2, 0, 1));

        assertThat(message).isEqualTo("Configuration apply completed. applyId=7, "
                + "status=SUCCEEDED, successCount=2, failedCount=0, skippedCount=1");
    }

    @Test
    void usesDefaultMessageWhenExceptionHasNoMessage() {
        assertThat(messages.failure(new IllegalStateException(), "기본 오류"))
                .isEqualTo("기본 오류");
    }
}
