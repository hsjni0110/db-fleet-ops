package com.dbfleetops.database.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dbfleetops.database.domain.DatabaseCredential;

public interface DatabaseCredentialRepository extends JpaRepository<DatabaseCredential, Long> {
    Optional<DatabaseCredential> findByDatabaseId(Long databaseId);
}