package com.dbfleetops.database.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.dto.DatabaseCreateRequest;
import com.dbfleetops.database.dto.DatabaseResponse;
import com.dbfleetops.database.dto.DatabaseUpdateRequest;
import com.dbfleetops.database.dto.RegisterManagedDatabaseRequest;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.backup.domain.BusinessVerificationProfile;
import com.dbfleetops.backup.domain.BusinessVerificationProfiles;
import com.dbfleetops.backup.infra.CodeBusinessVerificationProfiles;
import com.dbfleetops.agent.infra.AgentRepository;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class DatabaseInventoryService {

    private final ManagedDatabaseRepository databaseRepository;
    private final DatabaseCredentialRepository credentialRepository;
    private final DatabaseConnectionValidator connectionValidator;
    private final AgentRepository agentRepository;
    private final CredentialCipher credentialCipher;
    private final BusinessVerificationProfiles businessVerificationProfiles;

    @Autowired
    public DatabaseInventoryService(ManagedDatabaseRepository managedDatabaseRepository,
            DatabaseCredentialRepository databaseCredentialRepository,
            DatabaseConnectionValidator connectionValidator, AgentRepository agentRepository,
            CredentialCipher credentialCipher,
            BusinessVerificationProfiles businessVerificationProfiles) {
        this.databaseRepository = managedDatabaseRepository;
        this.credentialRepository = databaseCredentialRepository;
        this.connectionValidator = connectionValidator;
        this.agentRepository = agentRepository;
        this.credentialCipher = credentialCipher;
        this.businessVerificationProfiles = businessVerificationProfiles;
    }

    public DatabaseInventoryService(ManagedDatabaseRepository managedDatabaseRepository,
            DatabaseCredentialRepository databaseCredentialRepository,
            DatabaseConnectionValidator connectionValidator) {
        this(managedDatabaseRepository, databaseCredentialRepository, connectionValidator, null,
                new CredentialCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
                new CodeBusinessVerificationProfiles());
    }

    @Transactional
    public DatabaseResponse create(DatabaseCreateRequest request) {
        connectionValidator.validate(request);

        ManagedDatabase database = ManagedDatabase.register(new RegisterManagedDatabaseRequest(
                request.name(), request.host(), request.port(), request.databaseName(),
                request.engine(), request.environment(), request.serviceName(), request.owner(),
                request.description()));
        connectBusinessVerificationProfile(database, request.businessVerificationProfileKey());

        ManagedDatabase savedDatabase = databaseRepository.save(database);
        assignAgent(savedDatabase, request.assignedAgentId());

        DatabaseCredential credential = new DatabaseCredential(savedDatabase.getId(),
                request.username(), credentialCipher.encrypt(request.password()));

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
        database.changeConnection(request.host(), request.port(), request.databaseName(),
                request.engine());
        database.changeMetadata(request.name(), request.environment(), request.serviceName(),
                request.owner(), request.description());
        assignAgent(database, request.assignedAgentId());
        connectBusinessVerificationProfile(database, request.businessVerificationProfileKey());

        DatabaseCredential credential = credentialRepository.findByDatabaseId(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Credential not found. databaseId=" + databaseId));

        credential.changeCredentials(request.username(), credentialCipher.encrypt(request.password()));

        return DatabaseResponse.from(database);
    }

    @Transactional
    public void deactivate(Long databaseId) {
        ManagedDatabase database = getDatabase(databaseId);
        database.deactivate();
    }

    @Transactional
    public DatabaseResponse connectBusinessVerificationProfile(Long databaseId, String profileKey) {
        ManagedDatabase database = getDatabase(databaseId);
        connectBusinessVerificationProfile(database, profileKey);
        return DatabaseResponse.from(database);
    }

    private ManagedDatabase getDatabase(Long databaseId) {
        return databaseRepository.findById(databaseId).orElseThrow(
                () -> new IllegalArgumentException("Database not found. databaseId=" + databaseId));
    }

    private void assignAgent(ManagedDatabase database, Long agentId) {
        if (agentId != null && (agentRepository == null || !agentRepository.existsById(agentId))) {
            throw new IllegalArgumentException("Agent not found. agentId=" + agentId);
        }
        database.assignAgent(agentId);
    }

    private void connectBusinessVerificationProfile(ManagedDatabase database, String profileKey) {
        if (profileKey == null || profileKey.isBlank()) {
            database.connectBusinessVerificationProfile(null);
            return;
        }

        BusinessVerificationProfile profile = businessVerificationProfiles.find(profileKey.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "업무 검증 Profile을 찾을 수 없습니다. profileKey=" + profileKey));
        if (profile.engine() != database.getEngine()) {
            throw new IllegalArgumentException(
                    "업무 검증 Profile과 관리 Database의 DBMS가 일치하지 않습니다. profileKey="
                            + profile.key() + ", profileEngine=" + profile.engine()
                            + ", databaseEngine=" + database.getEngine());
        }
        database.connectBusinessVerificationProfile(profile.key());
    }
}
