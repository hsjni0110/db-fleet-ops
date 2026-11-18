package com.dbfleetops.backup.infra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeBusinessVerificationProfilesTest {

    @Test
    void insuranceProfileContainsRequiredTablesAndSevenRules() {
        var profile = new CodeBusinessVerificationProfiles()
                .find(CodeBusinessVerificationProfiles.INSURANCE_SANDBOX_V1).orElseThrow();

        assertThat(profile.requiredTables()).containsExactly("customer", "policy",
                "premium_payment", "claim", "claim_payment");
        assertThat(profile.rules()).hasSize(7).allSatisfy(rule -> {
            assertThat(rule.query()).startsWith("SELECT");
            assertThat(rule.timeoutMillis()).isEqualTo(5_000L);
        });
    }
}
