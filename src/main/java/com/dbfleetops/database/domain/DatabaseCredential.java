package com.dbfleetops.database.domain;

import lombok.Getter;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;

@Getter
@Entity
public class DatabaseCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long databaseId;

    private String username;

    @Column(name = "password", length = 1000)
    private String encryptedPassword;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected DatabaseCredential() {}

    public DatabaseCredential(Long databaseId, String username, String encryptedPassword) {
        validateDatabaseId(databaseId);
        validateCredentials(username, encryptedPassword);

        this.databaseId = databaseId;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void changeCredentials(String username, String encryptedPassword) {
        validateCredentials(username, encryptedPassword);

        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateDatabaseId(Long databaseId) {
        notNull(databaseId, "데이터베이스 ID는 필수입니다.");
    }

    public String revealPassword(com.dbfleetops.database.application.CredentialCipher cipher) {
        return cipher.decrypt(encryptedPassword);
    }

    private static void validateCredentials(String username, String password) {
        hasText(username, "데이터베이스 사용자 이름은 필수입니다.");
        hasText(password, "데이터베이스 비밀번호는 필수입니다.");
    }

}
