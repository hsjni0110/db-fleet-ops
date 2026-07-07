package com.dbfleetops.policy.infra;

import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationApplyRepository extends JpaRepository<ConfigurationApply, Long> {

    Optional<ConfigurationApply> findByOperationJobId(Long operationJobId);

    Optional<ConfigurationApply> findFirstByDatabaseIdOrderByCreatedAtDesc(Long databaseId);

    List<ConfigurationApply> findTop10ByDatabaseIdOrderByCreatedAtDesc(Long databaseId);

    List<ConfigurationApply> findByDatabaseIdAndStatusOrderByCreatedAtDesc(Long databaseId,
            ConfigurationApplyStatus status);

    boolean existsByDatabaseIdAndStatusIn(Long databaseId, List<ConfigurationApplyStatus> statuses);
}
