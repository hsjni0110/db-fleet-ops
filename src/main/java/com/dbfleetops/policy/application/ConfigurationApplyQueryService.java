package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.dto.ConfigurationApplyItemResponse;
import com.dbfleetops.policy.dto.ConfigurationApplyResponse;
import com.dbfleetops.policy.infra.ConfigurationApplyItemRepository;
import com.dbfleetops.policy.infra.ConfigurationApplyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigurationApplyQueryService {

    private final ConfigurationApplyRepository applyRepository;
    private final ConfigurationApplyItemRepository applyItemRepository;

    public ConfigurationApplyQueryService(ConfigurationApplyRepository applyRepository,
            ConfigurationApplyItemRepository applyItemRepository) {
        this.applyRepository = applyRepository;
        this.applyItemRepository = applyItemRepository;
    }

    @Transactional(readOnly = true)
    public ConfigurationApplyResponse getApply(Long applyId) {
        if (applyId == null) {
            throw new IllegalArgumentException("applyId is required.");
        }

        ConfigurationApply apply =
                applyRepository.findById(applyId).orElseThrow(() -> new IllegalArgumentException(
                        "Configuration apply not found. applyId=" + applyId));

        return toDetailResponse(apply);
    }

    @Transactional(readOnly = true)
    public ConfigurationApplyResponse getApplyByJobId(Long jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId is required.");
        }

        ConfigurationApply apply = applyRepository.findByOperationJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configuration apply not found. jobId=" + jobId));

        return toDetailResponse(apply);
    }

    private ConfigurationApplyResponse toDetailResponse(ConfigurationApply apply) {
        var items = applyItemRepository.findByApplyIdOrderByParameterNameAsc(apply.getId()).stream()
                .map(ConfigurationApplyItemResponse::from).toList();

        return ConfigurationApplyResponse.from(apply, items);
    }
}
