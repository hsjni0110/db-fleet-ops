package com.dbfleetops.policy.infra;

import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationSnapshotItemRepository
        extends JpaRepository<ConfigurationSnapshotItem, Long> {

    List<ConfigurationSnapshotItem> findBySnapshotIdOrderByParameterNameAsc(Long snapshotId);

    Optional<ConfigurationSnapshotItem> findBySnapshotIdAndParameterName(Long snapshotId,
            String parameterName);
}
