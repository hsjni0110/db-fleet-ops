package task

import (
	"context"
	"encoding/json"
	"fmt"
	"testing"

	"db-fleetops-agent/internal/infra/restore"
	"db-fleetops-agent/internal/port"
)

type fakeRestoreRunner struct {
	runCalled     bool
	cleanupCalled bool
	runRequest    restore.MySQLRestoreRequest
	cleanupRequest restore.MySQLRestoreCleanupRequest
	runResult     restore.MySQLRestoreResult
	runErr        error
	cleanupErr    error
}

func (r *fakeRestoreRunner) Run(
	ctx context.Context,
	request restore.MySQLRestoreRequest,
) (restore.MySQLRestoreResult, error) {
	r.runCalled = true
	r.runRequest = request

	if r.runErr != nil {
		return r.runResult, r.runErr
	}

	if r.runResult.Status == "" {
		r.runResult =
			restore.MySQLRestoreResult{
				Status:                "RESTORED",
				BackupFile:            request.BackupFile,
				TemporaryDatabaseName: request.TemporaryDatabaseName,
				Message:               "restored",
			}
	}

	return r.runResult, nil
}

func (r *fakeRestoreRunner) Cleanup(
	ctx context.Context,
	request restore.MySQLRestoreCleanupRequest,
) error {
	r.cleanupCalled = true
	r.cleanupRequest = request

	return r.cleanupErr
}

type fakeRestoreVerifier struct {
	called  bool
	request restore.MySQLRestoreVerifyRequest
	result  restore.MySQLRestoreVerifyResult
	err     error
}

func (v *fakeRestoreVerifier) Verify(
	ctx context.Context,
	request restore.MySQLRestoreVerifyRequest,
) (restore.MySQLRestoreVerifyResult, error) {
	v.called = true
	v.request = request

	if v.err != nil {
		return v.result, v.err
	}

	if v.result.Status == "" {
		rowCount := int64(10)

		v.result =
			restore.MySQLRestoreVerifyResult{
				Status:                "VERIFIED",
				TemporaryDatabaseName: request.TemporaryDatabaseName,
				RestoredTableCount:    1,
				CheckedTableCount:     1,
				TotalRowCount:         10,
				Items: []restore.MySQLRestoreVerifyItemResult{
					{
						TableName:          "orders",
						ExistsInRestoredDB: true,
						RowCount:           &rowCount,
						Status:             "VERIFIED",
						Message:            "table verified",
					},
				},
				Message: "restore verification completed",
			}
	}

	return v.result, nil
}

func TestMySQLRestoreVerifyHandlerSupportsRestoreVerifyTask(t *testing.T) {
	handler :=
		NewMySQLRestoreVerifyHandlerWithDependencies(
			&fakeRestoreRunner{},
			&fakeRestoreVerifier{},
		)

	if !handler.Supports(TaskTypeMySQLRestoreVerify) {
		t.Fatalf("expected handler to support MYSQL_RESTORE_VERIFY")
	}

	if handler.Supports(TaskTypeMySQLLogicalBackup) {
		t.Fatalf("restore verify handler must not support MYSQL_LOGICAL_BACKUP")
	}
}

func TestMySQLRestoreVerifyHandlerReturnsVerifiedResult(t *testing.T) {
	runner :=
		&fakeRestoreRunner{}

	verifier :=
		&fakeRestoreVerifier{}

	handler :=
		NewMySQLRestoreVerifyHandlerWithDependencies(
			runner,
			verifier,
		)

	parametersJSON :=
		`{
			"operationJobId": 100,
			"databaseId": 1,
			"backupTaskId": 200,
			"sourceDatabaseName": "orders",
			"backupFile": "/tmp/orders.sql",
			"host": "127.0.0.1",
			"port": 3306,
			"username": "restore_user",
			"password": "secret",
			"temporaryDatabaseName": "restore_verify_orders_100",
			"expectedTables": ["orders"],
			"verifyRowCount": true,
			"cleanup": true
		}`

	resultJSON, err :=
		handler.Handle(
			context.Background(),
			port.Task{
				TaskType:       TaskTypeMySQLRestoreVerify,
				ParametersJSON: parametersJSON,
			},
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	var result MySQLRestoreVerifyResult

	if err := json.Unmarshal(
		[]byte(resultJSON),
		&result,
	); err != nil {
		t.Fatalf("failed to unmarshal result: %v", err)
	}

	if result.Status != "VERIFIED" {
		t.Fatalf("expected VERIFIED, got %s", result.Status)
	}

	if result.OperationJobID != 100 {
		t.Fatalf("expected operationJobId 100, got %d", result.OperationJobID)
	}

	if result.RestoredTableCount != 1 {
		t.Fatalf("expected restored table count 1, got %d", result.RestoredTableCount)
	}

	if !runner.runCalled {
		t.Fatalf("expected runner to be called")
	}

	if !verifier.called {
		t.Fatalf("expected verifier to be called")
	}

	if !runner.cleanupCalled {
		t.Fatalf("expected cleanup to be called")
	}

	if runner.runRequest.Cleanup {
		t.Fatalf("runner Run must keep temporary database before verification")
	}

	if runner.cleanupRequest.TemporaryDatabaseName != "restore_verify_orders_100" {
		t.Fatalf("unexpected cleanup database name: %s", runner.cleanupRequest.TemporaryDatabaseName)
	}
}

func TestMySQLRestoreVerifyHandlerBuildsTemporaryDatabaseNameWhenMissing(t *testing.T) {
	runner :=
		&fakeRestoreRunner{}

	verifier :=
		&fakeRestoreVerifier{}

	handler :=
		NewMySQLRestoreVerifyHandlerWithDependencies(
			runner,
			verifier,
		)

	parametersJSON :=
		`{
			"operationJobId": 100,
			"databaseId": 1,
			"backupTaskId": 200,
			"sourceDatabaseName": "orders-prod",
			"backupFile": "/tmp/orders.sql",
			"host": "127.0.0.1",
			"port": 3306,
			"username": "restore_user",
			"password": "secret",
			"expectedTables": ["orders"],
			"verifyRowCount": true,
			"cleanup": false
		}`

	resultJSON, err :=
		handler.Handle(
			context.Background(),
			port.Task{
				TaskType:       TaskTypeMySQLRestoreVerify,
				ParametersJSON: parametersJSON,
			},
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	var result MySQLRestoreVerifyResult

	if err := json.Unmarshal(
		[]byte(resultJSON),
		&result,
	); err != nil {
		t.Fatalf("failed to unmarshal result: %v", err)
	}

	if result.TemporaryDatabaseName == "" {
		t.Fatalf("expected temporary database name")
	}

	if runner.runRequest.TemporaryDatabaseName == "" {
		t.Fatalf("expected runner temporary database name")
	}
}

func TestMySQLRestoreVerifyHandlerReturnsFailedWhenRunnerFails(t *testing.T) {
	runner :=
		&fakeRestoreRunner{
			runResult: restore.MySQLRestoreResult{
				Status:       "FAILED",
				ErrorCode:    "RESTORE_FAILED",
				ErrorMessage: "restore failed",
			},
			runErr: fmt.Errorf("restore failed"),
		}

	verifier :=
		&fakeRestoreVerifier{}

	handler :=
		NewMySQLRestoreVerifyHandlerWithDependencies(
			runner,
			verifier,
		)

	parametersJSON :=
		`{
			"operationJobId": 100,
			"databaseId": 1,
			"backupTaskId": 200,
			"sourceDatabaseName": "orders",
			"backupFile": "/tmp/orders.sql",
			"host": "127.0.0.1",
			"port": 3306,
			"username": "restore_user",
			"password": "secret",
			"temporaryDatabaseName": "restore_verify_orders_100",
			"cleanup": true
		}`

	resultJSON, err :=
		handler.Handle(
			context.Background(),
			port.Task{
				TaskType:       TaskTypeMySQLRestoreVerify,
				ParametersJSON: parametersJSON,
			},
		)

	if err == nil {
		t.Fatalf("expected error")
	}

	var result MySQLRestoreVerifyResult

	if unmarshalErr := json.Unmarshal(
		[]byte(resultJSON),
		&result,
	); unmarshalErr != nil {
		t.Fatalf("failed to unmarshal result: %v", unmarshalErr)
	}

	if result.Status != "FAILED" {
		t.Fatalf("expected FAILED, got %s", result.Status)
	}

	if verifier.called {
		t.Fatalf("verifier must not be called when restore runner fails")
	}
}

func TestMySQLRestoreVerifyHandlerReturnsCleanupFailedWhenCleanupFails(t *testing.T) {
	runner :=
		&fakeRestoreRunner{
			cleanupErr: fmt.Errorf("cleanup failed"),
		}

	verifier :=
		&fakeRestoreVerifier{}

	handler :=
		NewMySQLRestoreVerifyHandlerWithDependencies(
			runner,
			verifier,
		)

	parametersJSON :=
		`{
			"operationJobId": 100,
			"databaseId": 1,
			"backupTaskId": 200,
			"sourceDatabaseName": "orders",
			"backupFile": "/tmp/orders.sql",
			"host": "127.0.0.1",
			"port": 3306,
			"username": "restore_user",
			"password": "secret",
			"temporaryDatabaseName": "restore_verify_orders_100",
			"cleanup": true
		}`

	resultJSON, err :=
		handler.Handle(
			context.Background(),
			port.Task{
				TaskType:       TaskTypeMySQLRestoreVerify,
				ParametersJSON: parametersJSON,
			},
		)

	if err == nil {
		t.Fatalf("expected cleanup error")
	}

	var result MySQLRestoreVerifyResult

	if unmarshalErr := json.Unmarshal(
		[]byte(resultJSON),
		&result,
	); unmarshalErr != nil {
		t.Fatalf("failed to unmarshal result: %v", unmarshalErr)
	}

	if result.Status != "CLEANUP_FAILED" {
		t.Fatalf("expected CLEANUP_FAILED, got %s", result.Status)
	}

	if result.ErrorCode != "CLEANUP_FAILED" {
		t.Fatalf("expected CLEANUP_FAILED, got %s", result.ErrorCode)
	}
}