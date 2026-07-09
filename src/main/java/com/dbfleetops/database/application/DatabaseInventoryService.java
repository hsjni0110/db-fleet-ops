package com.dbfleetops.database.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.dto.DatabaseCreateRequest;
import com.dbfleetops.database.dto.DatabaseResponse;
import com.dbfleetops.database.dto.DatabaseUpdateRequest;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;

@Service
public class DatabaseInventoryService {

    private final ManagedDatabaseRepository databaseRepository;
    private final DatabaseCredentialRepository credentialRepository;
    private final DatabaseConnectionValidator connectionValidator;

    public DatabaseInventoryService(ManagedDatabaseRepository managedDatabaseRepository,
            DatabaseCredentialRepository databaseCredentialRepository,
            DatabaseConnectionValidator connectionValidator) {
        this.databaseRepository = managedDatabaseRepository;
        this.credentialRepository = databaseCredentialRepository;
        this.connectionValidator = connectionValidator;
    }

    @Transactional
    public DatabaseResponse create(DatabaseCreateRequest request) {
        connectionValidator.validate(request);

        ManagedDatabase database = new ManagedDatabase(request.name(), request.host(),
                request.port(), request.databaseName(), request.engine(), request.environment(),
                request.serviceName(), request.owner(), request.description());

        ManagedDatabase savedDatabase = databaseRepository.save(database);

        DatabaseCredential credential = new DatabaseCredential(savedDatabase.getId(),
                request.username(), request.password());

        credentialRepository.save(credential);
        return DatabaseResponse.from(savedDatabase);
    }

    @Transactional(readOnly = true)
    public List<DatabaseResponse> findAll() {
        return databaseRepository.findAll().stream().map(DatabaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DatabaseResponse findById(Long databaseId) {
        return DatabaseResponse.from(getDatabase(databaseId));
    }

    @Transactional
    public DatabaseResponse update(Long databaseId, DatabaseUpdateRequest request) {
        connectionValidator.validate(request);

        ManagedDatabase database = getDatabase(databaseId);
        database.update(request.name(), request.host(), request.port(), request.databaseName(),
                request.engine(), request.environment(), request.serviceName(), request.owner(),
                request.description());

        DatabaseCredential credential = credentialRepository.findByDatabaseId(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Credential not found. databaseId=" + databaseId));

        credential.update(request.username(), request.password());

        return DatabaseResponse.from(database);
    }

    @Transactional
    public void deactivate(Long databaseId) {
        ManagedDatabase database = getDatabase(databaseId);
        database.deactivate();
    }

    private ManagedDatabase getDatabase(Long databaseId) {
        return databaseRepository.findById(databaseId).orElseThrow(
                () -> new IllegalArgumentException("Database not found. databaseId=" + databaseId));
    }
}
