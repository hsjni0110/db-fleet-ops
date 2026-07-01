package com.dbfleetops.database.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ManagedDatabase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String host;
    private int port;
    private String databaseName;

    @Enumerated(EnumType.STRING)
    private DatabaseEngine engine;

    @Enumerated(EnumType.STRING)
    private DatabaseStatus status;

    private String environment;
    private String serviceName;
    private String owner;
    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected ManagedDatabase() {
    }

    public ManagedDatabase(
        String name, 
        String host, 
        int port, 
        String databaseName, 
        DatabaseEngine engine,
        String environment, 
        String serviceName, 
        String owner, 
        String description
    ) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.engine = engine;
        this.environment = environment;
        this.serviceName = serviceName;
        this.owner = owner;
        this.description = description;
        this.status = DatabaseStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void update(
        String name,
        String host,
        int port,
        String databaseName,
        DatabaseEngine engine,
        String environment,
        String serviceName,
        String owner,
        String description
    ) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.engine = engine;
        this.environment = environment;
        this.serviceName = serviceName;
        this.owner = owner;
        this.description = description;
    }

    public void deactivate() {
        this.status = DatabaseStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == DatabaseStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public DatabaseEngine getEngine() {
        return engine;
    }

    public DatabaseStatus getStatus() {
        return status;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getOwner() {
        return owner;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}