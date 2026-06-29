package com.dbfleetops.health.port;

import com.dbfleetops.health.domain.DatabaseHealth;

public interface DatabaseHealthProbe {
    
    DatabaseHealth check();
}
