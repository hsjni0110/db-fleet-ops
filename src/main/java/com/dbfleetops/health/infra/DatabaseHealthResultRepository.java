package com.dbfleetops.health.infra;

import com.dbfleetops.health.domain.DatabaseHealthResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatabaseHealthResultRepository extends JpaRepository<DatabaseHealthResult, Long> {

    List<DatabaseHealthResult> findByDatabaseIdOrderByCheckedAtDesc(Long databaseId);
}