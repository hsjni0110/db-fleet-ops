package com.dbfleetops.db_fleetops;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.dbfleetops.db_fleetops.config.TargetDatabaseProperties;

@SpringBootTest(properties = {
    "db-fleetops.target.host=localhost",
    "db-fleetops.target.port=3306",
    "db-fleetops.target.database=dbops_target",
    "db-fleetops.target.username=db_monitor",
    "db-fleetops.target.password=test_password",
    "db-fleetops.target.connect-timeout=2s",
    "db-fleetops.target.socket-timeout=3s"
})
public class TargetDatabasePropertiesTest {

    @Autowired
    private TargetDatabaseProperties properties;

    @Test
    void bindsTargetDatabaseProperties() {
        assertThat(properties.host()).isEqualTo("localhost");
        assertThat(properties.port()).isEqualTo(3306);
        assertThat(properties.database()).isEqualTo("dbops_target");
        assertThat(properties.username()).isEqualTo("db_monitor");
        assertThat(properties.password()).isEqualTo("test_password");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.socketTimeout()).isEqualTo(Duration.ofSeconds(3));
    }
}
