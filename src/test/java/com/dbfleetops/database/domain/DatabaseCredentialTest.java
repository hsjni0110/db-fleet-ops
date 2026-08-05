package com.dbfleetops.database.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DatabaseCredentialTest {

    @Test
    void createCredential() {
        DatabaseCredential credential = new DatabaseCredential(1L, "operator", "password");

        assertThat(credential.getDatabaseId()).isEqualTo(1L);
        assertThat(credential.getUsername()).isEqualTo("operator");
        assertThat(credential.getPassword()).isEqualTo("password");
        assertThat(credential.getCreatedAt()).isNotNull();
        assertThat(credential.getUpdatedAt()).isNotNull();
    }

    @Test
    void databaseIdIsRequired() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DatabaseCredential(null, "operator", "password"))
                .withMessage("데이터베이스 ID는 필수입니다.");
    }

    @Test
    void usernameIsRequired() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DatabaseCredential(1L, " ", "password"))
                .withMessage("데이터베이스 사용자 이름은 필수입니다.");
    }

    @Test
    void passwordIsRequired() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DatabaseCredential(1L, "operator", null))
                .withMessage("데이터베이스 비밀번호는 필수입니다.");
    }

    @Test
    void changeCredentials() {
        DatabaseCredential credential = new DatabaseCredential(1L, "operator", "password");

        credential.changeCredentials("new-operator", "new-password");

        assertThat(credential.getUsername()).isEqualTo("new-operator");
        assertThat(credential.getPassword()).isEqualTo("new-password");
        assertThat(credential.getUpdatedAt()).isNotNull();
    }

    @Test
    void invalidCredentialsDoNotChangeExistingCredentials() {
        DatabaseCredential credential = new DatabaseCredential(1L, "operator", "password");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> credential.changeCredentials("new-operator", " "))
                .withMessage("데이터베이스 비밀번호는 필수입니다.");

        assertThat(credential.getUsername()).isEqualTo("operator");
        assertThat(credential.getPassword()).isEqualTo("password");
    }
}
