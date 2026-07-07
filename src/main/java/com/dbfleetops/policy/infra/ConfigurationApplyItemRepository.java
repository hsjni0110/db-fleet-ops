package com.dbfleetops.policy.infra;

import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationApplyItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationApplyItemRepository
        extends JpaRepository<ConfigurationApplyItem, Long> {

    List<ConfigurationApplyItem> findByApplyIdOrderByParameterNameAsc(Long applyId);

    List<ConfigurationApplyItem> findByApplyIdAndApplyStatusOrderByParameterNameAsc(Long applyId,
            ConfigurationApplyItemStatus applyStatus);

    Optional<ConfigurationApplyItem> findByApplyIdAndParameterName(Long applyId,
            String parameterName);
}
