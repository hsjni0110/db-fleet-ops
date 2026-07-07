package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationApplyItemStatus;
import com.dbfleetops.policy.domain.ConfigurationApplyStatus;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.ConfigurationApplyResponse;
import com.dbfleetops.policy.infra.ConfigurationApplyItemRepository;
import com.dbfleetops.policy.infra.ConfigurationApplyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConfigurationApplyQueryServiceTest {

    private final ConfigurationApplyRepository applyRepository =
            mock(ConfigurationApplyRepository.class);

    private final ConfigurationApplyItemRepository applyItemRepository =
            mock(ConfigurationApplyItemRepository.class);

    private final ConfigurationApplyQueryService service =
            new ConfigurationApplyQueryService(applyRepository, applyItemRepository);

    @Test
    void getApplyReturnsApplyWithItems() {
        ConfigurationApply apply = createSucceededApply();

        ConfigurationApplyItem item = createVerifiedItem(apply.getId());

        when(applyRepository.findById(7L)).thenReturn(Optional.of(apply));

        when(applyItemRepository.findByApplyIdOrderByParameterNameAsc(7L))
                .thenReturn(List.of(item));

        ConfigurationApplyResponse response = service.getApply(7L);

        assertThat(response.applyId()).isEqualTo(7L);

        assertThat(response.databaseId()).isEqualTo(1L);

        assertThat(response.operationJobId()).isEqualTo(31L);

        assertThat(response.status()).isEqualTo(ConfigurationApplyStatus.SUCCEEDED);

        assertThat(response.beforeSnapshotId()).isEqualTo(12L);

        assertThat(response.afterSnapshotId()).isEqualTo(13L);

        assertThat(response.items()).hasSize(1);

        assertThat(response.items().get(0).parameterName()).isEqualTo("slow_query_log");

        assertThat(response.items().get(0).beforeValue()).isEqualTo("OFF");

        assertThat(response.items().get(0).afterValue()).isEqualTo("ON");

        assertThat(response.items().get(0).applyStatus())
                .isEqualTo(ConfigurationApplyItemStatus.VERIFIED);
    }

    @Test
    void getApplyByJobIdReturnsApplyWithItems() {
        ConfigurationApply apply = createSucceededApply();

        ConfigurationApplyItem item = createVerifiedItem(apply.getId());

        when(applyRepository.findByOperationJobId(31L)).thenReturn(Optional.of(apply));

        when(applyItemRepository.findByApplyIdOrderByParameterNameAsc(7L))
                .thenReturn(List.of(item));

        ConfigurationApplyResponse response = service.getApplyByJobId(31L);

        assertThat(response.applyId()).isEqualTo(7L);

        assertThat(response.operationJobId()).isEqualTo(31L);

        assertThat(response.items()).hasSize(1);
    }

    @Test
    void getApplyThrowsExceptionWhenApplyDoesNotExist() {
        when(applyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApply(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Configuration apply not found");
    }

    @Test
    void getApplyByJobIdThrowsExceptionWhenApplyDoesNotExist() {
        when(applyRepository.findByOperationJobId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApplyByJobId(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Configuration apply not found");
    }

    private ConfigurationApply createSucceededApply() {
        ConfigurationApply apply =
                ConfigurationApply.create(1L, 31L, "local-user", "enable slow query log", 1);

        ReflectionTestUtils.setField(apply, "id", 7L);

        apply.start(12L);

        apply.complete(13L, 1, 0, 0);

        return apply;
    }

    private ConfigurationApplyItem createVerifiedItem(Long applyId) {
        ConfigurationApplyItem item = ConfigurationApplyItem.create(applyId, "slow_query_log", "ON",
                ParameterValueType.BOOLEAN, true, true);

        ReflectionTestUtils.setField(item, "id", 1L);

        item.markBeforeValue("OFF");
        item.markApplied();
        item.markVerified("ON");

        return item;
    }
}
