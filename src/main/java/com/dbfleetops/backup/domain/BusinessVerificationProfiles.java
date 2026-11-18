package com.dbfleetops.backup.domain;

import java.util.List;
import java.util.Optional;

public interface BusinessVerificationProfiles {
    Optional<BusinessVerificationProfile> find(String key);

    List<BusinessVerificationProfile> findAll();
}
