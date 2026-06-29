package com.dbfleetops.db_fleetops.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "db-fleetops.target")
public record TargetDatabaseProperties (
    
    @NotBlank
    String host,

    @Min(1)
    @Max(65535)
    int port,

    @NotBlank
    String database,

    @NotBlank
    String username,

    @NotBlank
    String password,

    @NotNull
    Duration connectTimeout,

    @NotNull
    Duration socketTimeout

) {
}
