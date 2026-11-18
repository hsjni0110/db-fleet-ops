package com.dbfleetops.backup.api;

import com.dbfleetops.backup.domain.BusinessVerificationProfiles;
import com.dbfleetops.backup.dto.BusinessVerificationProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business-verification-profiles")
public class BusinessVerificationProfileController {

    private final BusinessVerificationProfiles profiles;

    public BusinessVerificationProfileController(BusinessVerificationProfiles profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public List<BusinessVerificationProfileResponse> findAll() {
        return profiles.findAll().stream().map(BusinessVerificationProfileResponse::from).toList();
    }
}
