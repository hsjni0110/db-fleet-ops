package com.dbfleetops.policy.infra;

import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationProfileRepository extends JpaRepository<ConfigurationProfile, Long> {

    Optional<ConfigurationProfile> findByProfileName(String profileName);

    List<ConfigurationProfile> findByEngineType(ConfigurationEngineType engineType);

    List<ConfigurationProfile> findByEngineTypeAndStatus(ConfigurationEngineType engineType,
            ConfigurationProfileStatus status);

    boolean existsByProfileName(String profileName);
}
