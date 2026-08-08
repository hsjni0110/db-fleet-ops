package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ConfigurationApplyStatus;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationItem;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
import com.dbfleetops.policy.dto.ConfigurationChangeItem;
import com.dbfleetops.policy.dto.ConfigurationChangeRequest;
import com.dbfleetops.policy.infra.ConfigurationApplyRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileParameterRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ConfigurationApplyValidationService {

    private final ConfigurationProfileRepository profileRepository;
    private final ConfigurationProfileParameterRepository profileParameterRepository;
    private final ConfigurationApplyRepository applyRepository;

    public ConfigurationApplyValidationService(ConfigurationProfileRepository profileRepository,
            ConfigurationProfileParameterRepository profileParameterRepository,
            ConfigurationApplyRepository applyRepository) {
        this.profileRepository = profileRepository;
        this.profileParameterRepository = profileParameterRepository;
        this.applyRepository = applyRepository;
    }

    @Transactional(readOnly = true)
    public ConfigurationApplyValidationResult validate(Long databaseId,
            ConfigurationChangeRequest request) {
        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }

        validateRequestShape(request);
        validateNoRunningApply(databaseId);
        validateDuplicateParameters(request.items());

        ConfigurationProfile profile = profileRepository.findById(request.profileId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configuration profile not found. profileId=" + request.profileId()));

        List<ConfigurationApplyValidationItem> items = request.items().stream()
                .map(parameter -> validateParameter(profile.getId(), parameter)).toList();

        return new ConfigurationApplyValidationResult(databaseId, profile.getId(), items);
    }

    private ConfigurationApplyValidationItem validateParameter(Long profileId,
            ConfigurationChangeItem requestParameter) {
        String parameterName = normalizeParameterName(requestParameter.parameterName());

        ConfigurationProfileParameter profileParameter =
                profileParameterRepository.findByProfileIdAndParameterName(profileId, parameterName)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Configuration profile parameter not found. profileId=" + profileId
                                        + ", parameterName=" + requestParameter.parameterName()));

        validateApplyPolicy(profileParameter);
        validateTargetValue(requestParameter.targetValue(), profileParameter.getValueType(),
                profileParameter.getParameterName());

        return new ConfigurationApplyValidationItem(profileParameter.getParameterName(),
                requestParameter.targetValue().trim(), profileParameter.getValueType(),
                profileParameter.getDynamic(), profileParameter.getApplyAllowed());
    }

    private void validateRequestShape(ConfigurationChangeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }

        if (request.profileId() == null) {
            throw new IllegalArgumentException("profileId is required.");
        }

        if (request.requestedBy() == null || request.requestedBy().isBlank()) {
            throw new IllegalArgumentException("requestedBy is required.");
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("parameters is required.");
        }

        for (ConfigurationChangeItem parameter : request.items()) {
            if (parameter == null) {
                throw new IllegalArgumentException("parameter is required.");
            }

            if (parameter.parameterName() == null || parameter.parameterName().isBlank()) {
                throw new IllegalArgumentException("parameterName is required.");
            }

            if (parameter.targetValue() == null || parameter.targetValue().isBlank()) {
                throw new IllegalArgumentException("targetValue is required.");
            }
        }
    }

    private void validateNoRunningApply(Long databaseId) {
        boolean exists = applyRepository.existsByDatabaseIdAndStatusIn(databaseId,
                List.of(ConfigurationApplyStatus.REQUESTED, ConfigurationApplyStatus.RUNNING));

        if (exists) {
            throw new IllegalStateException(
                    "Another configuration apply is already requested or running. databaseId="
                            + databaseId);
        }
    }

    private void validateDuplicateParameters(List<ConfigurationChangeItem> parameters) {
        Set<String> names = new HashSet<>();

        for (ConfigurationChangeItem parameter : parameters) {
            String normalizedName = normalizeParameterName(parameter.parameterName());

            if (!names.add(normalizedName)) {
                throw new IllegalArgumentException(
                        "Duplicate parameterName is not allowed. parameterName="
                                + parameter.parameterName());
            }
        }
    }

    private void validateApplyPolicy(ConfigurationProfileParameter profileParameter) {
        if (!Boolean.TRUE.equals(profileParameter.getDynamic())) {
            throw new IllegalStateException(
                    "Static parameter is not supported in configuration apply MVP. parameterName="
                            + profileParameter.getParameterName());
        }

        if (!Boolean.TRUE.equals(profileParameter.getApplyAllowed())) {
            throw new IllegalStateException(
                    "Parameter is not allowed to be applied by platform. parameterName="
                            + profileParameter.getParameterName());
        }
    }

    private void validateTargetValue(String targetValue, ParameterValueType valueType,
            String parameterName) {
        if (valueType == null) {
            throw new IllegalArgumentException(
                    "Parameter valueType is required. parameterName=" + parameterName);
        }

        switch (valueType) {
            case BOOLEAN -> validateBooleanValue(targetValue, parameterName);
            case NUMBER -> validateNumberValue(targetValue, parameterName);
            case STRING -> validateStringValue(targetValue, parameterName);
        }
    }

    private void validateBooleanValue(String targetValue, String parameterName) {
        String normalized = targetValue.trim().toUpperCase(Locale.ROOT);

        if (!List.of("ON", "OFF", "TRUE", "FALSE", "1", "0", "YES", "NO", "Y", "N")
                .contains(normalized)) {
            throw new IllegalArgumentException("Invalid BOOLEAN targetValue. parameterName="
                    + parameterName + ", targetValue=" + targetValue);
        }
    }

    private void validateNumberValue(String targetValue, String parameterName) {
        try {
            new BigDecimal(targetValue.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid NUMBER targetValue. parameterName="
                    + parameterName + ", targetValue=" + targetValue);
        }
    }

    private void validateStringValue(String targetValue, String parameterName) {
        String value = targetValue.trim();

        if (value.contains(";") || value.contains("--") || value.contains("/*")
                || value.contains("*/")) {
            throw new IllegalArgumentException(
                    "Unsafe STRING targetValue. parameterName=" + parameterName);
        }
    }

    private String normalizeParameterName(String parameterName) {
        return parameterName.trim().toLowerCase(Locale.ROOT);
    }
}
