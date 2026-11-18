package com.dbfleetops.backup.infra;

import com.dbfleetops.backup.domain.BusinessRuleResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessRuleResultRepository extends JpaRepository<BusinessRuleResult, Long> {
    List<BusinessRuleResult> findByVerificationIdOrderByRuleCodeAsc(Long verificationId);
}
