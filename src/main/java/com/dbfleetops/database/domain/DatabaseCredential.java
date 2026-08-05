package com.dbfleetops.database.domain;

import lombok.Getter;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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
        this.databaseId = databaseId;
        this.username = username;
        this.password = password;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String username, String password) {
        this.username = username;
        this.password = password;
        this.updatedAt = LocalDateTime.now();
    }



}
