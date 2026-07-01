package com.dbfleetops.health.application;

import com.dbfleetops.database.domain.DatabaseEngine;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseHealthAdapterFactory {

    private final List<DatabaseHealthAdapter> adapters;

    public DatabaseHealthAdapterFactory(List<DatabaseHealthAdapter> adapters) {
        this.adapters = adapters;
    }

    public DatabaseHealthAdapter getAdapter(DatabaseEngine engine) {
        return adapters.stream()
                .filter(adapter -> adapter.supports() == engine)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported database engine: " + engine));
    }
}