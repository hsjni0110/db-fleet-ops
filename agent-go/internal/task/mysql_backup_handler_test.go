package task

import (
	"context"
	"strings"
	"testing"

	"db-fleetops-agent/internal/port"
)

func TestMySQLBackupHandlerSupportsMySQLLogicalBackup(t *testing.T) {
	handler := NewMySQLBackupHandler("/tmp/db-fleetops-backups")

	if !handler.Supports("MYSQL_LOGICAL_BACKUP") {
		t.Fatal("expected handler to support MYSQL_LOGICAL_BACKUP")
	}

	if handler.Supports("COLLECT_LINUX_STATUS") {
		t.Fatal("expected handler not to support COLLECT_LINUX_STATUS")
	}
}

func TestMySQLBackupHandlerReturnsStubBackupResult(t *testing.T) {
	handler := NewMySQLBackupHandler("/tmp/db-fleetops-backups")

	result, err := handler.Handle(
		context.Background(),
		port.Task{
			TaskID:   10,
			TaskType: "MYSQL_LOGICAL_BACKUP",
			ParametersJSON: `{
				"databaseName": "orders",
				"backupType": "LOGICAL",
				"compression": true
			}`,
		},
	)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !strings.Contains(result, `"status":"CREATED"`) {
		t.Fatalf("expected result to contain status CREATED, got %s", result)
	}

	if !strings.Contains(result, "orders-") {
		t.Fatalf("expected result to contain database name, got %s", result)
	}

	if !strings.Contains(result, ".sql") {
		t.Fatalf("expected result to contain sql file extension, got %s", result)
	}
}

func TestMySQLBackupHandlerReturnsErrorWhenParametersAreInvalidJSON(t *testing.T) {
	handler := NewMySQLBackupHandler("/tmp/db-fleetops-backups")

	_, err := handler.Handle(
		context.Background(),
		port.Task{
			TaskID:         10,
			TaskType:       "MYSQL_LOGICAL_BACKUP",
			ParametersJSON: "{invalid-json",
		},
	)

	if err == nil {
		t.Fatal("expected error, got nil")
	}
}