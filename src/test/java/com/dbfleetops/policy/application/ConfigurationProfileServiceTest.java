package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import com.dbfleetops.policy.domain.ConfigurationProfileStatus;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.AddConfigurationProfileParameterRequest;
import com.dbfleetops.policy.dto.ConfigurationProfileParameterResponse;
import com.dbfleetops.policy.dto.ConfigurationProfileResponse;
import com.dbfleetops.policy.dto.CreateConfigurationProfileRequest;
import com.dbfleetops.policy.infra.ConfigurationProfileParameterRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConfigurationProfileServiceTest {

    private final ConfigurationProfileRepository profileRepository =
            mock(ConfigurationProfileRepository.class);

    private final ConfigurationProfileParameterRepository parameterRepository =
            mock(ConfigurationProfileParameterRepository.class);

    private final ConfigurationProfileService service =
            new ConfigurationProfileService(profileRepository, parameterRepository);

    @Test
    void createProfileCreatesDraftProfile() {
        when(profileRepository.existsByProfileName("mysql-production-standard")).thenReturn(false);

        when(profileRepository.save(any(ConfigurationProfile.class))).thenAnswer(invocation -> {
            ConfigurationProfile profile = invocation.getArgument(0);
            ReflectionTestUtils.setField(profile, "id", 1L);
            return profile;
        });

        ConfigurationProfileResponse response =
                service.createProfile(new CreateConfigurationProfileRequest(
                        "mysql-production-standard", ConfigurationEngineType.MYSQL, "PRODUCTION",
                        ">=8.0", "MySQL production baseline profile"));

        assertThat(response.profileId()).isEqualTo(1L);

        assertThat(response.profileName()).isEqualTo("mysql-production-standard");

        assertThat(response.engineType()).isEqualTo(ConfigurationEngineType.MYSQL);

        assertThat(response.status()).isEqualTo(ConfigurationProfileStatus.DRAFT);
    }

    @Test
    void createProfileThrowsExceptionWhenProfileNameAlreadyExists() {
        when(profileRepository.existsByProfileName("mysql-production-standard")).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile(new CreateConfigurationProfileRequest(
                "mysql-production-standard", ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile"))).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("already exists");
    }

    @Test
    void getProfileReturnsProfileWithParameters() {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ReflectionTestUtils.setField(profile, "id", 1L);

        ConfigurationProfileParameter parameter = ConfigurationProfileParameter.create(1L,
                "slow_query_log", "ON", ParameterValueType.BOOLEAN, true, true, true,
                "Production DB should enable slow query log.");

        ReflectionTestUtils.setField(parameter, "id", 10L);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(parameterRepository.findByProfileIdOrderByParameterNameAsc(1L))
                .thenReturn(List.of(parameter));

        ConfigurationProfileResponse response = service.getProfile(1L);

        assertThat(response.profileId()).isEqualTo(1L);

        assertThat(response.parameters()).hasSize(1);

        assertThat(response.parameters().getFirst().parameterName()).isEqualTo("slow_query_log");
    }

    @Test
    void addParameterCreatesParameter() {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ReflectionTestUtils.setField(profile, "id", 1L);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(parameterRepository.existsByProfileIdAndParameterName(1L, "slow_query_log"))
                .thenReturn(false);

        when(parameterRepository.save(any(ConfigurationProfileParameter.class)))
                .thenAnswer(invocation -> {
                    ConfigurationProfileParameter parameter = invocation.getArgument(0);

                    ReflectionTestUtils.setField(parameter, "id", 10L);

                    return parameter;
                });

        ConfigurationProfileParameterResponse response = service.addParameter(1L,
                new AddConfigurationProfileParameterRequest("slow_query_log", "ON",
                        ParameterValueType.BOOLEAN, true, true, true,
                        "Production DB should enable slow query log."));

        assertThat(response.parameterId()).isEqualTo(10L);

        assertThat(response.profileId()).isEqualTo(1L);

        assertThat(response.parameterName()).isEqualTo("slow_query_log");

        assertThat(response.expectedValue()).isEqualTo("ON");

        assertThat(response.valueType()).isEqualTo(ParameterValueType.BOOLEAN);

        assertThat(response.dynamic()).isTrue();

        assertThat(response.applyAllowed()).isTrue();
    }

    @Test
    void addParameterThrowsExceptionWhenDuplicated() {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ReflectionTestUtils.setField(profile, "id", 1L);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(parameterRepository.existsByProfileIdAndParameterName(1L, "slow_query_log"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addParameter(1L,
                new AddConfigurationProfileParameterRequest("slow_query_log", "ON",
                        ParameterValueType.BOOLEAN, true, true, true,
                        "Production DB should enable slow query log.")))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("already exists");
    }

    @Test
    void activateProfileChangesStatusToActive() {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ReflectionTestUtils.setField(profile, "id", 1L);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(profileRepository.save(any(ConfigurationProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(parameterRepository.findByProfileIdOrderByParameterNameAsc(1L)).thenReturn(List.of());

        ConfigurationProfileResponse response = service.activateProfile(1L);

        assertThat(response.status()).isEqualTo(ConfigurationProfileStatus.ACTIVE);
    }

    @Test
    void getProfilesCanFilterByEngineType() {
        ConfigurationProfile mysqlProfile = ConfigurationProfile.create("mysql-production-standard",
                ConfigurationEngineType.MYSQL, "PRODUCTION", ">=8.0",
                "MySQL production baseline profile");

        ReflectionTestUtils.setField(mysqlProfile, "id", 1L);

        when(profileRepository.findByEngineType(ConfigurationEngineType.MYSQL))
                .thenReturn(List.of(mysqlProfile));

        List<ConfigurationProfileResponse> responses =
                service.getProfiles(ConfigurationEngineType.MYSQL);

        assertThat(responses).hasSize(1);

        assertThat(responses.getFirst().engineType()).isEqualTo(ConfigurationEngineType.MYSQL);
    }
}
