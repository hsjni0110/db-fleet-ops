package com.dbfleetops.policy.integration;

import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationApplyItemStatus;
import com.dbfleetops.policy.domain.ConfigurationApplyStatus;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.infra.ConfigurationApplyItemRepository;
import com.dbfleetops.policy.infra.ConfigurationApplyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ConfigurationApplyPersistenceTest {

    @Autowired
    private ConfigurationApplyRepository applyRepository;

    @Autowired
    private ConfigurationApplyItemRepository applyItemRepository;

    @Test
    void saveConfigurationApplyAndItems() {
        ConfigurationApply apply =
                ConfigurationApply.create(1L, 100L, "local-user", "enable slow query log", 2);

        ConfigurationApply savedApply = applyRepository.save(apply);

        ConfigurationApplyItem slowQueryLog = ConfigurationApplyItem.create(savedApply.getId(),
                "slow_query_log", "ON", ParameterValueType.BOOLEAN, true, true);

        ConfigurationApplyItem longQueryTime = ConfigurationApplyItem.create(savedApply.getId(),
                "long_query_time", "1.0", ParameterValueType.NUMBER, true, true);

        applyItemRepository.saveAll(List.of(slowQueryLog, longQueryTime));

        List<ConfigurationApplyItem> items =
                applyItemRepository.findByApplyIdOrderByParameterNameAsc(savedApply.getId());

        assertThat(savedApply.getId()).isNotNull();

        assertThat(savedApply.getStatus()).isEqualTo(ConfigurationApplyStatus.REQUESTED);

        assertThat(savedApply.getDatabaseId()).isEqualTo(1L);

        assertThat(savedApply.getOperationJobId()).isEqualTo(100L);

        assertThat(savedApply.getTotalCount()).isEqualTo(2);

        assertThat(items).hasSize(2);

        assertThat(items).extracting(ConfigurationApplyItem::getParameterName)
                .containsExactly("long_query_time", "slow_query_log");
    }

    @Test
    void startAndCompleteApply() {
        ConfigurationApply apply =
                ConfigurationApply.create(1L, 100L, "local-user", "enable slow query log", 1);

        ConfigurationApply savedApply = applyRepository.save(apply);

        savedApply.start(10L);
        savedApply.complete(11L, 1, 0, 0);

        ConfigurationApply foundApply = applyRepository.findById(savedApply.getId()).orElseThrow();

        assertThat(foundApply.getStatus()).isEqualTo(ConfigurationApplyStatus.SUCCEEDED);

        assertThat(foundApply.getBeforeSnapshotId()).isEqualTo(10L);

        assertThat(foundApply.getAfterSnapshotId()).isEqualTo(11L);

        assertThat(foundApply.getSuccessCount()).isEqualTo(1);

        assertThat(foundApply.getFailedCount()).isZero();

        assertThat(foundApply.getCompletedAt()).isNotNull();
    }

    @Test
    void completeApplyAsPartiallySucceededWhenSomeItemsFailed() {
        ConfigurationApply apply =
                ConfigurationApply.create(1L, 100L, "local-user", "apply mixed parameters", 2);

        ConfigurationApply savedApply = applyRepository.save(apply);

        savedApply.start(10L);
        savedApply.complete(11L, 1, 1, 0);

        ConfigurationApply foundApply = applyRepository.findById(savedApply.getId()).orElseThrow();

        assertThat(foundApply.getStatus()).isEqualTo(ConfigurationApplyStatus.PARTIALLY_SUCCEEDED);

        assertThat(foundApply.getSuccessCount()).isEqualTo(1);

        assertThat(foundApply.getFailedCount()).isEqualTo(1);
    }

    @Test
    void applyItemStatusTransitions() {
        ConfigurationApply apply = applyRepository.save(
                ConfigurationApply.create(1L, 100L, "local-user", "enable slow query log", 1));

        ConfigurationApplyItem item = ConfigurationApplyItem.create(apply.getId(), "slow_query_log",
                "ON", ParameterValueType.BOOLEAN, true, true);

        ConfigurationApplyItem savedItem = applyItemRepository.save(item);

        savedItem.markBeforeValue("OFF");
        savedItem.markApplied();
        savedItem.markVerified("ON");

        ConfigurationApplyItem foundItem =
                applyItemRepository.findById(savedItem.getId()).orElseThrow();

        assertThat(foundItem.getApplyStatus()).isEqualTo(ConfigurationApplyItemStatus.VERIFIED);

        assertThat(foundItem.getBeforeValue()).isEqualTo("OFF");

        assertThat(foundItem.getAfterValue()).isEqualTo("ON");

        assertThat(foundItem.getAppliedAt()).isNotNull();

        assertThat(foundItem.getVerifiedAt()).isNotNull();
    }

    @Test
    void findApplyByOperationJobId() {
        ConfigurationApply apply = applyRepository.save(
                ConfigurationApply.create(1L, 100L, "local-user", "enable slow query log", 1));

        assertThat(applyRepository.findByOperationJobId(100L)).isPresent().get()
                .extracting(ConfigurationApply::getId).isEqualTo(apply.getId());
    }

    @Test
    void existsRunningApplyByDatabaseId() {
        ConfigurationApply apply = applyRepository.save(
                ConfigurationApply.create(1L, 100L, "local-user", "enable slow query log", 1));

        apply.start(10L);

        boolean exists = applyRepository.existsByDatabaseIdAndStatusIn(1L,
                List.of(ConfigurationApplyStatus.RUNNING));

        assertThat(exists).isTrue();
    }
}
