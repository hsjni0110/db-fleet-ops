package com.dbfleetops.database.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    protected DatabaseCredential() {
    }

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

    public Long getId() {
        return id;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}