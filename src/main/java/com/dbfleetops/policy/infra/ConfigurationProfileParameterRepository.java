package com.dbfleetops.policy.infra;

import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationProfileParameterRepository
        extends JpaRepository<ConfigurationProfileParameter, Long> {

    List<ConfigurationProfileParameter> findByProfileIdOrderByParameterNameAsc(Long profileId);

    Optional<ConfigurationProfileParameter> findByProfileIdAndParameterName(Long profileId,
            String parameterName);

    Optional<ConfigurationProfileParameter> findFirstByParameterNameIgnoreCaseOrderByIdDesc(
            String parameterName);

    boolean existsByProfileIdAndParameterName(Long profileId, String parameterName);
}
