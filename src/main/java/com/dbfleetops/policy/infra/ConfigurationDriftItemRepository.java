package com.dbfleetops.policy.infra;

import com.dbfleetops.policy.domain.ComplianceStatus;
import com.dbfleetops.policy.domain.ConfigurationDriftItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfigurationDriftItemRepository
        extends JpaRepository<ConfigurationDriftItem, Long> {

    List<ConfigurationDriftItem> findByDriftIdOrderByParameterNameAsc(Long driftId);

    List<ConfigurationDriftItem> findByDriftIdAndComplianceStatusOrderByParameterNameAsc(
            Long driftId, ComplianceStatus complianceStatus);
}
