package com.dbfleetops.operation.shared.application.required;

import java.util.Optional;

/** Operation이 Task 실행에 필요한 Database 계정 정보를 찾을 때 사용하는 출구입니다. */
public interface CredentialReader {
    /** Credential ID로 계정 정보를 조회하며, 존재하지 않으면 빈 값을 반환합니다. */
    Optional<CredentialReference> findCredential(Long credentialId);

    /** Database ID에 연결된 계정 정보를 조회하며, 존재하지 않으면 빈 값을 반환합니다. */
    Optional<CredentialReference> findCredentialByDatabase(Long databaseId);

    /** 암호화되어 저장된 비밀번호를 Task 실행에 사용할 수 있도록 복호화합니다. */
    ResolvedTaskCredential resolve(Long credentialId);
}
