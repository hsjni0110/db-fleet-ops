package com.dbfleetops.policy.api;

import com.dbfleetops.policy.application.ConfigurationProfileService;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.dto.AddConfigurationProfileParameterRequest;
import com.dbfleetops.policy.dto.ConfigurationProfileParameterResponse;
import com.dbfleetops.policy.dto.ConfigurationProfileResponse;
import com.dbfleetops.policy.dto.CreateConfigurationProfileRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/configuration-profiles")
public class ConfigurationProfileController {

    private final ConfigurationProfileService profileService;

    public ConfigurationProfileController(ConfigurationProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigurationProfileResponse createProfile(
            @RequestBody CreateConfigurationProfileRequest request) {
        return profileService.createProfile(request);
    }

    @GetMapping
    public List<ConfigurationProfileResponse> getProfiles(
            @RequestParam(required = false) ConfigurationEngineType engineType) {
        return profileService.getProfiles(engineType);
    }

    @GetMapping("/{profileId}")
    public ConfigurationProfileResponse getProfile(@PathVariable Long profileId) {
        return profileService.getProfile(profileId);
    }

    @PostMapping("/{profileId}/activate")
    public ConfigurationProfileResponse activateProfile(@PathVariable Long profileId) {
        return profileService.activateProfile(profileId);
    }

    @PostMapping("/{profileId}/deactivate")
    public ConfigurationProfileResponse deactivateProfile(@PathVariable Long profileId) {
        return profileService.deactivateProfile(profileId);
    }

    @PostMapping("/{profileId}/parameters")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigurationProfileParameterResponse addParameter(@PathVariable Long profileId,
            @RequestBody AddConfigurationProfileParameterRequest request) {
        return profileService.addParameter(profileId, request);
    }

    @GetMapping("/{profileId}/parameters")
    public List<ConfigurationProfileParameterResponse> getParameters(@PathVariable Long profileId) {
        return profileService.getParameters(profileId);
    }
}
