package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ParameterValueType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationValueComparatorTest {

    private final ConfigurationValueComparator comparator = new ConfigurationValueComparator();

    @Test
    void stringValuesAreComparedCaseInsensitively() {
        boolean matched = comparator.matches("ROW", "row", ParameterValueType.STRING);

        assertThat(matched).isTrue();
    }

    @Test
    void numberValuesAreComparedByNumericMeaning() {
        boolean matched = comparator.matches("1.0", "1.000000", ParameterValueType.NUMBER);

        assertThat(matched).isTrue();
    }

    @Test
    void differentNumberValuesDoNotMatch() {
        boolean matched = comparator.matches("1.0", "2.0", ParameterValueType.NUMBER);

        assertThat(matched).isFalse();
    }

    @Test
    void booleanOnAndOneAreSameMeaning() {
        boolean matched = comparator.matches("ON", "1", ParameterValueType.BOOLEAN);

        assertThat(matched).isTrue();
    }

    @Test
    void booleanOffAndFalseAreSameMeaning() {
        boolean matched = comparator.matches("OFF", "false", ParameterValueType.BOOLEAN);

        assertThat(matched).isTrue();
    }

    @Test
    void invalidBooleanValueDoesNotMatch() {
        boolean matched = comparator.matches("ON", "enabled", ParameterValueType.BOOLEAN);

        assertThat(matched).isFalse();
    }

    @Test
    void nullActualValueDoesNotMatch() {
        boolean matched = comparator.matches("ON", null, ParameterValueType.BOOLEAN);

        assertThat(matched).isFalse();
    }
}
