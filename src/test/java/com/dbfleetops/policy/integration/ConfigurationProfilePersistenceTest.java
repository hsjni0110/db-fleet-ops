package com.dbfleetops.policy.integration;

import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import com.dbfleetops.policy.domain.ConfigurationProfileStatus;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.infra.ConfigurationProfileParameterRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ConfigurationProfilePersistenceTest {

    @Autowired
    private ConfigurationProfileRepository profileRepository;

    @Autowired
    private ConfigurationProfileParameterRepository parameterRepository;

    @Test
    @DisplayName("ConfigurationProfile을 저장하고 조회할 수 있다")
    void saveAndFindConfigurationProfile() {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ConfigurationProfile savedProfile = profileRepository.save(profile);

        assertThat(savedProfile.getId()).isNotNull();

        assertThat(savedProfile.getProfileName()).isEqualTo("mysql-production-standard");

        assertThat(savedProfile.getEngineType()).isEqualTo(ConfigurationEngineType.MYSQL);

        assertThat(savedProfile.getStatus()).isEqualTo(ConfigurationProfileStatus.DRAFT);
    }

    @Test
    @DisplayName("Profile을 ACTIVE 상태로 변경할 수 있다")
    void activateConfigurationProfile() {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ConfigurationProfile savedProfile = profileRepository.save(profile);

        savedProfile.activate();

        ConfigurationProfile activatedProfile = profileRepository.save(savedProfile);

        assertThat(activatedProfile.getStatus()).isEqualTo(ConfigurationProfileStatus.ACTIVE);
    }

    @Test
    @DisplayName("ConfigurationProfileParameter를 저장하고 Profile ID 기준으로 조회할 수 있다")
    void saveAndFindConfigurationProfileParameters() {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ConfigurationProfile savedProfile = profileRepository.save(profile);

        ConfigurationProfileParameter slowQueryLog = ConfigurationProfileParameter.create(
                savedProfile.getId(), "slow_query_log", "ON", ParameterValueType.BOOLEAN, true,
                true, true, "Production DB should enable slow query log.");

        ConfigurationProfileParameter longQueryTime = ConfigurationProfileParameter.create(
                savedProfile.getId(), "long_query_time", "1.0", ParameterValueType.NUMBER, true,
                true, true, "Slow query threshold should be 1 second.");

        parameterRepository.save(slowQueryLog);
        parameterRepository.save(longQueryTime);

        List<ConfigurationProfileParameter> parameters =
                parameterRepository.findByProfileIdOrderByParameterNameAsc(savedProfile.getId());

        assertThat(parameters).hasSize(2);

        assertThat(parameters).extracting(ConfigurationProfileParameter::getParameterName)
                .containsExactly("long_query_time", "slow_query_log");
    }

    @Test
    @DisplayName("Profile 이름으로 중복 여부를 확인할 수 있다")
    void existsByProfileName() {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        profileRepository.save(profile);

        boolean exists = profileRepository.existsByProfileName("mysql-production-standard");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Profile ID와 Parameter 이름으로 중복 여부를 확인할 수 있다")
    void existsByProfileIdAndParameterName() {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ConfigurationProfile savedProfile = profileRepository.save(profile);

        ConfigurationProfileParameter parameter = ConfigurationProfileParameter.create(
                savedProfile.getId(), "slow_query_log", "ON", ParameterValueType.BOOLEAN, true,
                true, true, "Production DB should enable slow query log.");

        parameterRepository.save(parameter);

        boolean exists = parameterRepository.existsByProfileIdAndParameterName(savedProfile.getId(),
                "slow_query_log");

        assertThat(exists).isTrue();
    }
}
