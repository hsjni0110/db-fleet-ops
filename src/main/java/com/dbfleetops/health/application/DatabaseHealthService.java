package com.dbfleetops.health.application;

import org.springframework.stereotype.Service;

import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.port.DatabaseHealthProbe;

@Service
public class DatabaseHealthService {

    private final DatabaseHealthProbe databaseHealthProbe;

    public DatabaseHealthService(
        DatabaseHealthProbe databaseHealthProbe
    ) {
        this.databaseHealthProbe = databaseHealthProbe;
    }

    public DatabaseHealth checkDatabaseHealth() {
        return databaseHealthProbe.check();
    }
}