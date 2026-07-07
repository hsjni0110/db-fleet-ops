package com.dbfleetops.policy.application;

import com.dbfleetops.operation.dto.ConfigurationApplyParameterRequest;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.policy.domain.ConfigurationApplyStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
import com.dbfleetops.policy.infra.ConfigurationApplyRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileParameterRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConfigurationApplyValidationServiceTest {

    private final ConfigurationProfileRepository profileRepository =
            mock(ConfigurationProfileRepository.class);

    private final ConfigurationProfileParameterRepository profileParameterRepository =
            mock(ConfigurationProfileParameterRepository.class);

    private final ConfigurationApplyRepository applyRepository =
            mock(ConfigurationApplyRepository.class);

    private final ConfigurationApplyValidationService service =
            new ConfigurationApplyValidationService(profileRepository, profileParameterRepository,
                    applyRepository);

    @Test
    void validateReturnsValidatedItems() {
        ConfigurationProfile profile = createProfile(1L);

        ConfigurationProfileParameter slowQueryLog = createProfileParameter(10L, 1L,
                "slow_query_log", "ON", ParameterValueType.BOOLEAN, true, true);

        ConfigurationProfileParameter longQueryTime = createProfileParameter(11L, 1L,
                "long_query_time", "1.0", ParameterValueType.NUMBER, true, true);

        when(applyRepository.existsByDatabaseIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(profileParameterRepository.findByProfileIdAndParameterName(1L, "slow_query_log"))
                .thenReturn(Optional.of(slowQueryLog));

        when(profileParameterRepository.findByProfileIdAndParameterName(1L, "long_query_time"))
                .thenReturn(Optional.of(longQueryTime));

        ConfigurationApplyValidationResult result = service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON"),
                                new ConfigurationApplyParameterRequest("long_query_time", "1.0"))));

        assertThat(result.databaseId()).isEqualTo(1L);

        assertThat(result.profileId()).isEqualTo(1L);

        assertThat(result.totalCount()).isEqualTo(2);

        assertThat(result.items()).extracting(item -> item.parameterName())
                .containsExactly("slow_query_log", "long_query_time");
    }

    @Test
    void validateThrowsExceptionWhenDuplicateParameterExists() {
        assertThatThrownBy(() -> service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "duplicate test",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON"),
                                new ConfigurationApplyParameterRequest("SLOW_QUERY_LOG", "OFF")))))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Duplicate parameterName");
    }

    @Test
    void validateThrowsExceptionWhenRunningApplyExists() {
        when(applyRepository.existsByDatabaseIdAndStatusIn(eq(1L),
                eq(List.of(ConfigurationApplyStatus.REQUESTED, ConfigurationApplyStatus.RUNNING))))
                        .thenReturn(true);

        assertThatThrownBy(() -> service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON")))))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("already requested or running");
    }

    @Test
    void validateThrowsExceptionWhenProfileDoesNotExist() {
        when(applyRepository.existsByDatabaseIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        when(profileRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "profile missing",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON")))))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Configuration profile not found");
    }

    @Test
    void validateThrowsExceptionWhenProfileParameterDoesNotExist() {
        ConfigurationProfile profile = createProfile(1L);

        when(applyRepository.existsByDatabaseIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(profileParameterRepository.findByProfileIdAndParameterName(1L, "unknown_parameter"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "invalid parameter",
                        List.of(new ConfigurationApplyParameterRequest("unknown_parameter",
                                "ON"))))).isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining(
                                                "Configuration profile parameter not found");
    }

    @Test
    void validateThrowsExceptionWhenParameterIsStatic() {
        ConfigurationProfile profile = createProfile(1L);

        ConfigurationProfileParameter parameter = createProfileParameter(10L, 1L,
                "innodb_log_file_size", "512M", ParameterValueType.STRING, false, true);

        when(applyRepository.existsByDatabaseIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(profileParameterRepository.findByProfileIdAndParameterName(1L, "innodb_log_file_size"))
                .thenReturn(Optional.of(parameter));

        assertThatThrownBy(() -> service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "static parameter test",
                        List.of(new ConfigurationApplyParameterRequest("innodb_log_file_size",
                                "512M"))))).isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining("Static parameter is not supported");
    }

    @Test
    void validateThrowsExceptionWhenApplyIsNotAllowed() {
        ConfigurationProfile profile = createProfile(1L);

        ConfigurationProfileParameter parameter = createProfileParameter(10L, 1L, "sql_mode",
                "STRICT_TRANS_TABLES", ParameterValueType.STRING, true, false);

        when(applyRepository.existsByDatabaseIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(profileParameterRepository.findByProfileIdAndParameterName(1L, "sql_mode"))
                .thenReturn(Optional.of(parameter));

        assertThatThrownBy(() -> service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "not allowed test",
                        List.of(new ConfigurationApplyParameterRequest("sql_mode",
                                "STRICT_TRANS_TABLES"))))).isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining("not allowed");
    }

    @Test
    void validateThrowsExceptionWhenBooleanValueIsInvalid() {
        ConfigurationProfile profile = createProfile(1L);

        ConfigurationProfileParameter parameter = createProfileParameter(10L, 1L, "slow_query_log",
                "ON", ParameterValueType.BOOLEAN, true, true);

        when(applyRepository.existsByDatabaseIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(profileParameterRepository.findByProfileIdAndParameterName(1L, "slow_query_log"))
                .thenReturn(Optional.of(parameter));

        assertThatThrownBy(() -> service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "invalid boolean",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log",
                                "enabled"))))).isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Invalid BOOLEAN targetValue");
    }

    @Test
    void validateThrowsExceptionWhenNumberValueIsInvalid() {
        ConfigurationProfile profile = createProfile(1L);

        ConfigurationProfileParameter parameter = createProfileParameter(10L, 1L, "long_query_time",
                "1.0", ParameterValueType.NUMBER, true, true);

        when(applyRepository.existsByDatabaseIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(profileParameterRepository.findByProfileIdAndParameterName(1L, "long_query_time"))
                .thenReturn(Optional.of(parameter));

        assertThatThrownBy(() -> service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "invalid number",
                        List.of(new ConfigurationApplyParameterRequest("long_query_time", "abc")))))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Invalid NUMBER targetValue");
    }

    @Test
    void validateThrowsExceptionWhenStringValueIsUnsafe() {
        ConfigurationProfile profile = createProfile(1L);

        ConfigurationProfileParameter parameter = createProfileParameter(10L, 1L, "sql_mode",
                "STRICT_TRANS_TABLES", ParameterValueType.STRING, true, true);

        when(applyRepository.existsByDatabaseIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(profileParameterRepository.findByProfileIdAndParameterName(1L, "sql_mode"))
                .thenReturn(Optional.of(parameter));

        assertThatThrownBy(() -> service.validate(1L,
                new CreateConfigurationApplyJobRequest(1L, "local-user", "unsafe string",
                        List.of(new ConfigurationApplyParameterRequest("sql_mode",
                                "abc'; DROP DATABASE mysql; --")))))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Unsafe STRING targetValue");
    }

    private ConfigurationProfile createProfile(Long profileId) {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ReflectionTestUtils.setField(profile, "id", profileId);

        return profile;
    }

    private ConfigurationProfileParameter createProfileParameter(Long parameterId, Long profileId,
            String parameterName, String expectedValue, ParameterValueType valueType,
            Boolean dynamic, Boolean applyAllowed) {
        ConfigurationProfileParameter parameter =
                ConfigurationProfileParameter.create(profileId, parameterName, expectedValue,
                        valueType, true, dynamic, applyAllowed, "test parameter");

        ReflectionTestUtils.setField(parameter, "id", parameterId);

        return parameter;
    }
}
