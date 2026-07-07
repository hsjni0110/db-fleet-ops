package com.dbfleetops.policy.integration;

import com.dbfleetops.operation.dto.ConfigurationApplyParameterRequest;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.policy.application.ConfigurationApplyQueryService;
import com.dbfleetops.policy.application.ConfigurationApplyValidationService;
import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationApplyItemStatus;
import com.dbfleetops.policy.domain.ConfigurationApplyStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.ConfigurationApplyResponse;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
import com.dbfleetops.policy.infra.ConfigurationApplyItemRepository;
import com.dbfleetops.policy.infra.ConfigurationApplyRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileParameterRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotItemRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ConfigurationApplyFlowIntegrationTest {

    @Autowired
    private ConfigurationProfileRepository profileRepository;

    @Autowired
    private ConfigurationProfileParameterRepository profileParameterRepository;

    @Autowired
    private ConfigurationApplyRepository applyRepository;

    @Autowired
    private ConfigurationApplyItemRepository applyItemRepository;

    @Autowired
    private ConfigurationSnapshotRepository snapshotRepository;

    @Autowired
    private ConfigurationSnapshotItemRepository snapshotItemRepository;

    @Autowired
    private ConfigurationApplyValidationService validationService;

    @Autowired
    private ConfigurationApplyQueryService queryService;

    @Test
    void dynamicParametersAreValidatedSuccessfully() {
        ConfigurationProfile profile = saveProfile();

        saveProfileParameter(profile.getId(), "slow_query_log", "ON", ParameterValueType.BOOLEAN,
                true, true);

        saveProfileParameter(profile.getId(), "long_query_time", "1.0", ParameterValueType.NUMBER,
                true, true);

        ConfigurationApplyValidationResult result = validationService.validate(1L,
                new CreateConfigurationApplyJobRequest(profile.getId(), "local-user",
                        "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON"),
                                new ConfigurationApplyParameterRequest("long_query_time", "1.0"))));

        assertThat(result.databaseId()).isEqualTo(1L);

        assertThat(result.profileId()).isEqualTo(profile.getId());

        assertThat(result.totalCount()).isEqualTo(2);

        assertThat(result.items()).extracting(item -> item.parameterName())
                .containsExactly("slow_query_log", "long_query_time");
    }

    @Test
    void staticParameterIsRejected() {
        ConfigurationProfile profile = saveProfile();

        saveProfileParameter(profile.getId(), "innodb_log_file_size", "512M",
                ParameterValueType.STRING, false, true);

        assertThatThrownBy(() -> validationService.validate(1L,
                new CreateConfigurationApplyJobRequest(profile.getId(), "local-user",
                        "try static parameter",
                        List.of(new ConfigurationApplyParameterRequest("innodb_log_file_size",
                                "512M"))))).isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining("Static parameter is not supported");
    }

    @Test
    void parameterNotRegisteredInProfileIsRejected() {
        ConfigurationProfile profile = saveProfile();

        saveProfileParameter(profile.getId(), "slow_query_log", "ON", ParameterValueType.BOOLEAN,
                true, true);

        assertThatThrownBy(() -> validationService.validate(1L,
                new CreateConfigurationApplyJobRequest(profile.getId(), "local-user",
                        "unknown parameter",
                        List.of(new ConfigurationApplyParameterRequest("unknown_parameter",
                                "ON"))))).isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining(
                                                "Configuration profile parameter not found");
    }

    @Test
    void invalidBooleanValueIsRejected() {
        ConfigurationProfile profile = saveProfile();

        saveProfileParameter(profile.getId(), "slow_query_log", "ON", ParameterValueType.BOOLEAN,
                true, true);

        assertThatThrownBy(() -> validationService.validate(1L,
                new CreateConfigurationApplyJobRequest(profile.getId(), "local-user",
                        "invalid boolean",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log",
                                "enabled"))))).isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Invalid BOOLEAN targetValue");
    }

    @Test
    void invalidNumberValueIsRejected() {
        ConfigurationProfile profile = saveProfile();

        saveProfileParameter(profile.getId(), "long_query_time", "1.0", ParameterValueType.NUMBER,
                true, true);

        assertThatThrownBy(() -> validationService.validate(1L,
                new CreateConfigurationApplyJobRequest(profile.getId(), "local-user",
                        "invalid number",
                        List.of(new ConfigurationApplyParameterRequest("long_query_time", "abc")))))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Invalid NUMBER targetValue");
    }

    @Test
    void duplicateParameterRequestIsRejected() {
        ConfigurationProfile profile = saveProfile();

        saveProfileParameter(profile.getId(), "slow_query_log", "ON", ParameterValueType.BOOLEAN,
                true, true);

        assertThatThrownBy(() -> validationService.validate(1L,
                new CreateConfigurationApplyJobRequest(profile.getId(), "local-user",
                        "duplicate parameter",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON"),
                                new ConfigurationApplyParameterRequest("SLOW_QUERY_LOG", "OFF")))))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Duplicate parameterName");
    }

    @Test
    void runningApplyBlocksNewApplyRequestForSameDatabase() {
        ConfigurationApply runningApply =
                ConfigurationApply.create(1L, 100L, "local-user", "running apply", 1);

        ConfigurationApply savedApply = applyRepository.save(runningApply);

        savedApply.start(10L);

        ConfigurationProfile profile = saveProfile();

        saveProfileParameter(profile.getId(), "slow_query_log", "ON", ParameterValueType.BOOLEAN,
                true, true);

        assertThatThrownBy(() -> validationService.validate(1L,
                new CreateConfigurationApplyJobRequest(profile.getId(), "another-user",
                        "blocked apply",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON")))))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("already requested or running");
    }

    @Test
    void applyResultCanStoreBeforeAndAfterValues() {
        ConfigurationApply apply =
                ConfigurationApply.create(1L, 100L, "local-user", "enable slow query log", 1);

        ConfigurationApply savedApply = applyRepository.save(apply);

        ConfigurationSnapshot beforeSnapshot = snapshotRepository
                .save(ConfigurationSnapshot.create(1L, ConfigurationEngineType.MYSQL));

        snapshotItemRepository.save(ConfigurationSnapshotItem.create(beforeSnapshot.getId(),
                "slow_query_log", "OFF", null, "BOOLEAN", true, "GLOBAL"));

        savedApply.start(beforeSnapshot.getId());

        ConfigurationApplyItem item = ConfigurationApplyItem.create(savedApply.getId(),
                "slow_query_log", "ON", ParameterValueType.BOOLEAN, true, true);

        item.markBeforeValue("OFF");
        item.markApplied();
        item.markVerified("ON");

        applyItemRepository.save(item);

        ConfigurationSnapshot afterSnapshot = snapshotRepository
                .save(ConfigurationSnapshot.create(1L, ConfigurationEngineType.MYSQL));

        snapshotItemRepository.save(ConfigurationSnapshotItem.create(afterSnapshot.getId(),
                "slow_query_log", "ON", null, "BOOLEAN", true, "GLOBAL"));

        savedApply.complete(afterSnapshot.getId(), 1, 0, 0);

        ConfigurationApplyResponse response = queryService.getApply(savedApply.getId());

        assertThat(response.status()).isEqualTo(ConfigurationApplyStatus.SUCCEEDED);

        assertThat(response.beforeSnapshotId()).isEqualTo(beforeSnapshot.getId());

        assertThat(response.afterSnapshotId()).isEqualTo(afterSnapshot.getId());

        assertThat(response.successCount()).isEqualTo(1);

        assertThat(response.failedCount()).isZero();

        assertThat(response.items()).hasSize(1);

        assertThat(response.items().get(0).parameterName()).isEqualTo("slow_query_log");

        assertThat(response.items().get(0).beforeValue()).isEqualTo("OFF");

        assertThat(response.items().get(0).afterValue()).isEqualTo("ON");

        assertThat(response.items().get(0).applyStatus())
                .isEqualTo(ConfigurationApplyItemStatus.VERIFIED);
    }

    @Test
    void applyResultStoresFailedItemAndFailedStatus() {
        ConfigurationApply apply =
                ConfigurationApply.create(1L, 100L, "local-user", "apply with failure", 1);

        ConfigurationApply savedApply = applyRepository.save(apply);

        savedApply.start(10L);

        ConfigurationApplyItem item = ConfigurationApplyItem.create(savedApply.getId(),
                "long_query_time", "1.0", ParameterValueType.NUMBER, true, true);

        item.markBeforeValue("10.0");
        item.markFailed("VERIFY_VALUE_MISMATCH", "Requested value does not match after value.");

        applyItemRepository.save(item);

        savedApply.fail(0, 1, 0);

        ConfigurationApplyResponse response = queryService.getApply(savedApply.getId());

        assertThat(response.status()).isEqualTo(ConfigurationApplyStatus.FAILED);

        assertThat(response.successCount()).isZero();

        assertThat(response.failedCount()).isEqualTo(1);

        assertThat(response.items()).hasSize(1);

        assertThat(response.items().get(0).applyStatus())
                .isEqualTo(ConfigurationApplyItemStatus.FAILED);

        assertThat(response.items().get(0).failureCode()).isEqualTo("VERIFY_VALUE_MISMATCH");
    }

    private ConfigurationProfile saveProfile() {
        ConfigurationProfile profile = ConfigurationProfile.create(
                "mysql-production-standard-" + System.nanoTime(), ConfigurationEngineType.MYSQL,
                "PRODUCTION", ">=8.0", "MySQL production baseline profile");

        return profileRepository.save(profile);
    }

    private ConfigurationProfileParameter saveProfileParameter(Long profileId, String parameterName,
            String expectedValue, ParameterValueType valueType, Boolean dynamic,
            Boolean applyAllowed) {
        ConfigurationProfileParameter parameter =
                ConfigurationProfileParameter.create(profileId, parameterName, expectedValue,
                        valueType, true, dynamic, applyAllowed, "test parameter");

        return profileParameterRepository.save(parameter);
    }
}
