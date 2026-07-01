package com.dbfleetops.database.infra;

import com.dbfleetops.database.domain.ManagedDatabase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedDatabaseRepository extends JpaRepository<ManagedDatabase, Long> {
}