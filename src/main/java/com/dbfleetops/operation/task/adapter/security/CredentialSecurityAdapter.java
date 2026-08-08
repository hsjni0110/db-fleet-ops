package com.dbfleetops.operation.task.adapter.security;

import com.dbfleetops.database.application.CredentialCipher;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.operation.shared.application.required.CredentialReader;
import com.dbfleetops.operation.shared.application.required.CredentialReference;
import com.dbfleetops.operation.shared.application.required.ResolvedTaskCredential;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 암호화된 Database 계정 저장소와 복호화 기능을 Credential 조회 포트에 연결합니다. */
@Component
public class CredentialSecurityAdapter implements CredentialReader {
    private final DatabaseCredentialRepository credentials;
    private final CredentialCipher cipher;

    public CredentialSecurityAdapter(DatabaseCredentialRepository credentials, CredentialCipher cipher) {
        this.credentials = credentials;
        this.cipher = cipher;
    }

    @Override
    public Optional<CredentialReference> findCredential(Long credentialId) {
        return credentials.findById(credentialId)
                .map(value -> new CredentialReference(value.getId(), value.getDatabaseId()));
    }

    @Override
    public Optional<CredentialReference> findCredentialByDatabase(Long databaseId) {
        return credentials.findByDatabaseId(databaseId)
                .map(value -> new CredentialReference(value.getId(), value.getDatabaseId()));
    }

    @Override
    public ResolvedTaskCredential resolve(Long credentialId) {
        var credential = credentials.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found."));
        return new ResolvedTaskCredential(credential.getDatabaseId(), credential.getUsername(),
                credential.revealPassword(cipher));
    }
}
