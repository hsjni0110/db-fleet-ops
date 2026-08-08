package com.dbfleetops.operation.job.application.service;


import com.dbfleetops.operation.job.dto.ConfigurationApplyParameterRequest;
import com.dbfleetops.operation.job.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.job.dto.CreateConfigurationApplyJobRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobPayloadFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JobPayloadFactory factory = new JobPayloadFactory(objectMapper);

    @Test
    void writesBackupPayloadAsValidJson() throws Exception {
        String payload = factory.backup(new CreateBackupJobRequest(
                "quote \" and slash \\", "operator"));

        JsonNode json = objectMapper.readTree(payload);

        assertThat(json.get("reason").asText()).isEqualTo("quote \" and slash \\");
        assertThat(json.get("requestedBy").asText()).isEqualTo("operator");
    }

    @Test
    void writesConfigurationApplyParameters() throws Exception {
        String payload = factory.configurationApply(new CreateConfigurationApplyJobRequest(
                7L, "operator", null,
                List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON"))));

        JsonNode json = objectMapper.readTree(payload);

        assertThat(json.get("profileId").asLong()).isEqualTo(7L);
        assertThat(json.get("reason").asText()).isEmpty();
        assertThat(json.get("parameters").get(0).get("parameterName").asText())
                .isEqualTo("slow_query_log");
    }
}
