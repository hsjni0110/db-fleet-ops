package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import com.dbfleetops.policy.dto.AddConfigurationProfileParameterRequest;
import com.dbfleetops.policy.dto.ConfigurationProfileParameterResponse;
import com.dbfleetops.policy.dto.ConfigurationProfileResponse;
import com.dbfleetops.policy.dto.CreateConfigurationProfileRequest;
import com.dbfleetops.policy.infra.ConfigurationProfileParameterRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConfigurationProfileService {

    private final ConfigurationProfileRepository profileRepository;
    private final ConfigurationProfileParameterRepository parameterRepository;

    public ConfigurationProfileService(ConfigurationProfileRepository profileRepository,
            ConfigurationProfileParameterRepository parameterRepository) {
        this.profileRepository = profileRepository;
        this.parameterRepository = parameterRepository;
    }

    @Transactional
    public ConfigurationProfileResponse createProfile(CreateConfigurationProfileRequest request) {
        validateCreateProfileRequest(request);

        if (profileRepository.existsByProfileName(request.profileName())) {
            throw new IllegalArgumentException(
                    "Configuration profile already exists. profileName=" + request.profileName());
        }

        ConfigurationProfile profile =
                ConfigurationProfile.create(request.profileName(), request.engineType(),
                        request.environment(), request.versionRange(), request.description());

        ConfigurationProfile savedProfile = profileRepository.save(profile);

        return ConfigurationProfileResponse.from(savedProfile);
    }

    @Transactional(readOnly = true)
    public List<ConfigurationProfileResponse> getProfiles(ConfigurationEngineType engineType) {
        List<ConfigurationProfile> profiles;

        if (engineType == null) {
            profiles = profileRepository.findAll();
        } else {
            profiles = profileRepository.findByEngineType(engineType);
        }

        return profiles.stream().map(ConfigurationProfileResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ConfigurationProfileResponse getProfile(Long profileId) {
        ConfigurationProfile profile = getProfileOrThrow(profileId);

        List<ConfigurationProfileParameterResponse> parameters =
                parameterRepository.findByProfileIdOrderByParameterNameAsc(profile.getId()).stream()
                        .map(ConfigurationProfileParameterResponse::from).toList();

        return ConfigurationProfileResponse.from(profile, parameters);
    }

    @Transactional
    public ConfigurationProfileResponse activateProfile(Long profileId) {
        ConfigurationProfile profile = getProfileOrThrow(profileId);

        profile.activate();

        ConfigurationProfile savedProfile = profileRepository.save(profile);

        return getProfile(savedProfile.getId());
    }

    @Transactional
    public ConfigurationProfileResponse deactivateProfile(Long profileId) {
        ConfigurationProfile profile = getProfileOrThrow(profileId);

        profile.deactivate();

        ConfigurationProfile savedProfile = profileRepository.save(profile);

        return getProfile(savedProfile.getId());
    }

    @Transactional
    public ConfigurationProfileParameterResponse addParameter(Long profileId,
            AddConfigurationProfileParameterRequest request) {
        validateAddParameterRequest(request);

        getProfileOrThrow(profileId);

        if (parameterRepository.existsByProfileIdAndParameterName(profileId,
                request.parameterName())) {
            throw new IllegalArgumentException(
                    "Configuration profile parameter already exists. profileId=" + profileId
                            + ", parameterName=" + request.parameterName());
        }

        ConfigurationProfileParameter parameter =
                ConfigurationProfileParameter.create(profileId, request.parameterName(),
                        request.expectedValue(), request.valueType(), request.required(),
                        request.dynamic(), request.applyAllowed(), request.description());

        ConfigurationProfileParameter savedParameter = parameterRepository.save(parameter);

        return ConfigurationProfileParameterResponse.from(savedParameter);
    }

    @Transactional(readOnly = true)
    public List<ConfigurationProfileParameterResponse> getParameters(Long profileId) {
        getProfileOrThrow(profileId);

        return parameterRepository.findByProfileIdOrderByParameterNameAsc(profileId).stream()
                .map(ConfigurationProfileParameterResponse::from).toList();
    }

    private ConfigurationProfile getProfileOrThrow(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId is required.");
        }

        return profileRepository.findById(profileId).orElseThrow(() -> new IllegalArgumentException(
                "Configuration profile not found. profileId=" + profileId));
    }

    private void validateCreateProfileRequest(CreateConfigurationProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }

        validateRequiredText(request.profileName(), "profileName");

        if (request.engineType() == null) {
            throw new IllegalArgumentException("engineType is required.");
        }

        validateRequiredText(request.environment(), "environment");
    }

    private void validateAddParameterRequest(AddConfigurationProfileParameterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }

        validateRequiredText(request.parameterName(), "parameterName");

        validateRequiredText(request.expectedValue(), "expectedValue");

        if (request.valueType() == null) {
            throw new IllegalArgumentException("valueType is required.");
        }
    }

    private void validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }
}
