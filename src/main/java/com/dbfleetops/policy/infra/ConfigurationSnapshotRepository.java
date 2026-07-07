package com.dbfleetops.policy.infra;

import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationSnapshotRepository
        extends JpaRepository<ConfigurationSnapshot, Long> {

    Optional<ConfigurationSnapshot> findFirstByDatabaseIdOrderByCapturedAtDesc(Long databaseId);

    List<ConfigurationSnapshot> findTop10ByDatabaseIdOrderByCapturedAtDesc(Long databaseId);
}
