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

    private String password;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected DatabaseCredential() {}

    public DatabaseCredential(Long databaseId, String username, String password) {
        validateDatabaseId(databaseId);
        validateCredentials(username, password);

        this.databaseId = databaseId;
        this.username = username;
        this.password = password;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void changeCredentials(String username, String password) {
        validateCredentials(username, password);

        this.username = username;
        this.password = password;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateDatabaseId(Long databaseId) {
        notNull(databaseId, "데이터베이스 ID는 필수입니다.");
    }

    private static void validateCredentials(String username, String password) {
        hasText(username, "데이터베이스 사용자 이름은 필수입니다.");
        hasText(password, "데이터베이스 비밀번호는 필수입니다.");
    }

}
