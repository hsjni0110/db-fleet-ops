package com.dbfleetops.policy.infra;

import com.dbfleetops.policy.domain.ConfigurationDrift;
import com.dbfleetops.policy.domain.ConfigurationDriftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationDriftRepository extends JpaRepository<ConfigurationDrift, Long> {

    Optional<ConfigurationDrift> findFirstByDatabaseIdOrderByCheckedAtDesc(Long databaseId);

    List<ConfigurationDrift> findTop10ByDatabaseIdOrderByCheckedAtDesc(Long databaseId);

    List<ConfigurationDrift> findByDatabaseIdAndStatusOrderByCheckedAtDesc(Long databaseId,
            ConfigurationDriftStatus status);
}
