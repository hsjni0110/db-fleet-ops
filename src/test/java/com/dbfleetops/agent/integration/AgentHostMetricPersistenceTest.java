package com.dbfleetops.agent.integration;

import com.dbfleetops.agent.domain.AgentHostMetric;
import com.dbfleetops.agent.infra.AgentHostMetricRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AgentHostMetricPersistenceTest {

    @Autowired
    private AgentHostMetricRepository repository;

    @Test
    void saveAndFindAgentHostMetric() {
        AgentHostMetric metric = AgentHostMetric.create(1L, 12.5, 61.2, 70.1);

        AgentHostMetric savedMetric = repository.save(metric);

        AgentHostMetric foundMetric = repository.findById(savedMetric.getId()).orElseThrow();

        assertThat(foundMetric.getAgentId()).isEqualTo(1L);

        assertThat(foundMetric.getCpuUsagePercent()).isEqualTo(12.5);

        assertThat(foundMetric.getMemoryUsagePercent()).isEqualTo(61.2);

        assertThat(foundMetric.getDiskUsagePercent()).isEqualTo(70.1);

        assertThat(foundMetric.getCollectedAt()).isNotNull();
    }

    @Test
    void findLatestMetricsByAgentId() {
        repository.save(AgentHostMetric.create(1L, 10.0, 50.0, 60.0));

        repository.save(AgentHostMetric.create(1L, 20.0, 55.0, 65.0));

        List<AgentHostMetric> metrics = repository.findTop10ByAgentIdOrderByCollectedAtDesc(1L);

        assertThat(metrics).hasSize(2);
    }
}
