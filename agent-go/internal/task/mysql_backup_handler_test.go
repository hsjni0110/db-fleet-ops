package task

import (
	"context"
	"strings"
	"testing"

	"db-fleetops-agent/internal/infra/backup"
	"db-fleetops-agent/internal/port"
)

type fakeMySQLBackupRunner struct {
	called  bool
	request backup.MySQLDumpRequest
}

func (f *fakeMySQLBackupRunner) Run(
	ctx context.Context,
	request backup.MySQLDumpRequest,
) (backup.MySQLDumpResult, error) {
	f.called = true
	f.request = request

	return backup.MySQLDumpResult{
		Status:         "CREATED",
		BackupFile:     "/tmp/db-fleetops-backups/orders-20260706-120000.sql",
		FileSizeBytes:  1024,
		ChecksumSHA256: "checksum-001",
		CreatedAt:      "2026-07-06T12:00:00+09:00",
		Message:        "mysql logical backup completed",
	}, nil
}

func TestMySQLBackupHandlerSupportsMySQLLogicalBackup(t *testing.T) {
	handler :=
		NewMySQLBackupHandlerWithRunner(
			"/tmp/db-fleetops-backups",
			&fakeMySQLBackupRunner{},
		)

	if !handler.Supports("MYSQL_LOGICAL_BACKUP") {
		t.Fatal("expected handler to support MYSQL_LOGICAL_BACKUP")
	}

	if handler.Supports("COLLECT_LINUX_STATUS") {
		t.Fatal("expected handler not to support COLLECT_LINUX_STATUS")
	}
}

func TestMySQLBackupHandlerRunsBackupAndReturnsResult(t *testing.T) {
	runner :=
		&fakeMySQLBackupRunner{}

	handler :=
		NewMySQLBackupHandlerWithRunner(
			"/tmp/db-fleetops-backups",
			runner,
		)

	result, err :=
		handler.Handle(
			context.Background(),
			port.Task{
				TaskID:   10,
				TaskType: "MYSQL_LOGICAL_BACKUP",
				ParametersJSON: `{
					"databaseName": "orders",
					"host": "127.0.0.1",
					"port": 3306,
					"username": "backup_user",
					"password": "secret",
					"backupType": "LOGICAL",
					"compression": true
				}`,
			},
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !runner.called {
		t.Fatal("expected runner to be called")
	}

	if runner.request.DatabaseName != "orders" {
		t.Fatalf(
			"expected databaseName orders, got %s",
			runner.request.DatabaseName,
		)
	}

	if runner.request.Password != "secret" {
		t.Fatal("expected password to be passed to runner")
	}

	if !strings.Contains(result, `"status":"CREATED"`) {
		t.Fatalf("expected CREATED status, got %s", result)
	}

	if strings.Contains(result, "secret") {
		t.Fatal("expected result not to contain password")
	}
}

func TestMySQLBackupHandlerReturnsErrorWhenParametersAreInvalidJSON(t *testing.T) {
	handler :=
		NewMySQLBackupHandlerWithRunner(
			"/tmp/db-fleetops-backups",
			&fakeMySQLBackupRunner{},
		)

	_, err :=
		handler.Handle(
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