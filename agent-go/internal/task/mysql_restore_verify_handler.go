package task

import (
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"time"

	"db-fleetops-agent/internal/infra/restore"
	"db-fleetops-agent/internal/port"
)

const TaskTypeMySQLRestoreVerify = "MYSQL_RESTORE_VERIFY"

type MySQLRestoreVerifyRequest struct {
	OperationJobID        int64    `json:"operationJobId"`
	DatabaseID            int64    `json:"databaseId"`
	BackupTaskID          int64    `json:"backupTaskId"`
	SourceDatabaseName    string   `json:"sourceDatabaseName"`
	BackupFile            string   `json:"backupFile"`
	Host                  string   `json:"host"`
	Port                  int      `json:"port"`
	Username              string   `json:"username"`
	Password              string   `json:"password"`
	TemporaryDatabaseName string   `json:"temporaryDatabaseName"`
	ExpectedTables        []string `json:"expectedTables"`
	VerifyRowCount        bool     `json:"verifyRowCount"`
	Cleanup               bool     `json:"cleanup"`
}

type MySQLRestoreVerifyResult struct {
	Status                string                                      `json:"status"`
	OperationJobID        int64                                       `json:"operationJobId"`
	DatabaseID            int64                                       `json:"databaseId"`
	BackupTaskID          int64                                       `json:"backupTaskId"`
	SourceDatabaseName    string                                      `json:"sourceDatabaseName"`
	BackupFile            string                                      `json:"backupFile"`
	TemporaryDatabaseName string                                      `json:"temporaryDatabaseName"`
	RestoredTableCount    int                                         `json:"restoredTableCount"`
	CheckedTableCount     int                                         `json:"checkedTableCount"`
	TotalRowCount         int64                                       `json:"totalRowCount"`
	StartedAt             string                                      `json:"startedAt"`
	CompletedAt           string                                      `json:"completedAt"`
	Items                 []restore.MySQLRestoreVerifyItemResult      `json:"items"`
	Message               string                                      `json:"message"`
	ErrorCode             string                                      `json:"errorCode,omitempty"`
	ErrorMessage           string                                     `json:"errorMessage,omitempty"`
}

type MySQLRestoreRunner interface {
	Run(
		ctx context.Context,
		request restore.MySQLRestoreRequest,
	) (restore.MySQLRestoreResult, error)

	Cleanup(
		ctx context.Context,
		request restore.MySQLRestoreCleanupRequest,
	) error
}

type MySQLRestoreVerifier interface {
	Verify(
		ctx context.Context,
		request restore.MySQLRestoreVerifyRequest,
	) (restore.MySQLRestoreVerifyResult, error)
}

type MySQLRestoreVerifyHandler struct {
	runner   MySQLRestoreRunner
	verifier MySQLRestoreVerifier
}

func NewMySQLRestoreVerifyHandler() *MySQLRestoreVerifyHandler {
	return &MySQLRestoreVerifyHandler{
		runner:   restore.NewMySQLRestoreRunner(),
		verifier: restore.NewMySQLRestoreVerifier(),
	}
}

func NewMySQLRestoreVerifyHandlerWithDependencies(
	runner MySQLRestoreRunner,
	verifier MySQLRestoreVerifier,
) *MySQLRestoreVerifyHandler {
	return &MySQLRestoreVerifyHandler{
		runner:   runner,
		verifier: verifier,
	}
}

func (h *MySQLRestoreVerifyHandler) Supports(
	taskType string,
) bool {
	return taskType == TaskTypeMySQLRestoreVerify
}

func (h *MySQLRestoreVerifyHandler) Handle(
	ctx context.Context,
	task port.Task,
) (string, error) {
	startedAt :=
		time.Now()

	var request MySQLRestoreVerifyRequest

	if err := json.Unmarshal(
		[]byte(task.ParametersJSON),
		&request,
	); err != nil {
		return "", err
	}

	if err := validateMySQLRestoreVerifyTaskRequest(request); err != nil {
		return "", err
	}

	temporaryDatabaseName :=
		request.TemporaryDatabaseName

	if temporaryDatabaseName == "" {
		temporaryDatabaseName =
			buildTemporaryDatabaseName(
				request.SourceDatabaseName,
				request.OperationJobID,
				startedAt,
			)
	}

	restoreResult, err :=
		h.runner.Run(
			ctx,
			restore.MySQLRestoreRequest{
				Host:                  request.Host,
				Port:                  request.Port,
				Username:              request.Username,
				Password:              request.Password,
				BackupFile:            request.BackupFile,
				TemporaryDatabaseName: temporaryDatabaseName,
				Cleanup:               false,
			},
		)

	if err != nil {
		result :=
			MySQLRestoreVerifyResult{
				Status:                restoreResult.Status,
				OperationJobID:        request.OperationJobID,
				DatabaseID:            request.DatabaseID,
				BackupTaskID:          request.BackupTaskID,
				SourceDatabaseName:    request.SourceDatabaseName,
				BackupFile:            request.BackupFile,
				TemporaryDatabaseName: temporaryDatabaseName,
				StartedAt:             startedAt.Format(time.RFC3339),
				CompletedAt:           time.Now().Format(time.RFC3339),
				Message:               restoreResult.Message,
				ErrorCode:             restoreResult.ErrorCode,
				ErrorMessage:          restoreResult.ErrorMessage,
			}

		resultBytes, marshalErr :=
			json.Marshal(result)

		if marshalErr != nil {
			return "", marshalErr
		}

		return string(resultBytes), err
	}

	verifyResult, err :=
		h.verifier.Verify(
			ctx,
			restore.MySQLRestoreVerifyRequest{
				Host:                  request.Host,
				Port:                  request.Port,
				Username:              request.Username,
				Password:              request.Password,
				TemporaryDatabaseName: temporaryDatabaseName,
				ExpectedTables:        request.ExpectedTables,
				VerifyRowCount:        request.VerifyRowCount,
			},
		)

	cleanupErr :=
		cleanupTemporaryDatabase(
			ctx,
			h.runner,
			request,
			temporaryDatabaseName,
		)

	if err != nil {
		result :=
			MySQLRestoreVerifyResult{
				Status:                "FAILED",
				OperationJobID:        request.OperationJobID,
				DatabaseID:            request.DatabaseID,
				BackupTaskID:          request.BackupTaskID,
				SourceDatabaseName:    request.SourceDatabaseName,
				BackupFile:            request.BackupFile,
				TemporaryDatabaseName: temporaryDatabaseName,
				RestoredTableCount:    verifyResult.RestoredTableCount,
				CheckedTableCount:     verifyResult.CheckedTableCount,
				TotalRowCount:         verifyResult.TotalRowCount,
				StartedAt:             startedAt.Format(time.RFC3339),
				CompletedAt:           time.Now().Format(time.RFC3339),
				Items:                 verifyResult.Items,
				Message:               verifyResult.Message,
				ErrorCode:             verifyResult.ErrorCode,
				ErrorMessage:          verifyResult.ErrorMessage,
			}

		if cleanupErr != nil && result.ErrorMessage != "" {
			result.ErrorMessage =
				result.ErrorMessage + "; cleanup error: " + cleanupErr.Error()
		}

		resultBytes, marshalErr :=
			json.Marshal(result)

		if marshalErr != nil {
			return "", marshalErr
		}

		return string(resultBytes), err
	}

	if cleanupErr != nil {
		result :=
			MySQLRestoreVerifyResult{
				Status:                "CLEANUP_FAILED",
				OperationJobID:        request.OperationJobID,
				DatabaseID:            request.DatabaseID,
				BackupTaskID:          request.BackupTaskID,
				SourceDatabaseName:    request.SourceDatabaseName,
				BackupFile:            request.BackupFile,
				TemporaryDatabaseName: temporaryDatabaseName,
				RestoredTableCount:    verifyResult.RestoredTableCount,
				CheckedTableCount:     verifyResult.CheckedTableCount,
				TotalRowCount:         verifyResult.TotalRowCount,
				StartedAt:             startedAt.Format(time.RFC3339),
				CompletedAt:           time.Now().Format(time.RFC3339),
				Items:                 verifyResult.Items,
				Message:               "restore verification completed but cleanup failed",
				ErrorCode:             "CLEANUP_FAILED",
				ErrorMessage:          cleanupErr.Error(),
			}

		resultBytes, marshalErr :=
			json.Marshal(result)

		if marshalErr != nil {
			return "", marshalErr
		}

		return string(resultBytes), cleanupErr
	}

	result :=
		MySQLRestoreVerifyResult{
			Status:                "VERIFIED",
			OperationJobID:        request.OperationJobID,
			DatabaseID:            request.DatabaseID,
			BackupTaskID:          request.BackupTaskID,
			SourceDatabaseName:    request.SourceDatabaseName,
			BackupFile:            request.BackupFile,
			TemporaryDatabaseName: temporaryDatabaseName,
			RestoredTableCount:    verifyResult.RestoredTableCount,
			CheckedTableCount:     verifyResult.CheckedTableCount,
			TotalRowCount:         verifyResult.TotalRowCount,
			StartedAt:             startedAt.Format(time.RFC3339),
			CompletedAt:           time.Now().Format(time.RFC3339),
			Items:                 verifyResult.Items,
			Message:               "restore verification completed",
		}

	resultBytes, err :=
		json.Marshal(result)

	if err != nil {
		return "", err
	}

	return string(resultBytes), nil
}

func cleanupTemporaryDatabase(
	ctx context.Context,
	runner MySQLRestoreRunner,
	request MySQLRestoreVerifyRequest,
	temporaryDatabaseName string,
) error {
	if !request.Cleanup {
		return nil
	}

	return runner.Cleanup(
		ctx,
		restore.MySQLRestoreCleanupRequest{
			Host:                  request.Host,
			Port:                  request.Port,
			Username:              request.Username,
			Password:              request.Password,
			BackupFile:            request.BackupFile,
			TemporaryDatabaseName: temporaryDatabaseName,
		},
	)
}

func validateMySQLRestoreVerifyTaskRequest(
	request MySQLRestoreVerifyRequest,
) error {
	if request.OperationJobID <= 0 {
		return fmt.Errorf("operationJobId is required")
	}

	if request.DatabaseID <= 0 {
		return fmt.Errorf("databaseId is required")
	}

	if request.BackupTaskID <= 0 {
		return fmt.Errorf("backupTaskId is required")
	}

	if request.SourceDatabaseName == "" {
		return fmt.Errorf("sourceDatabaseName is required")
	}

	if request.BackupFile == "" {
		return fmt.Errorf("backupFile is required")
	}

	if request.Host == "" {
		return fmt.Errorf("host is required")
	}

	if request.Port <= 0 {
		return fmt.Errorf("port is required")
	}

	if request.Username == "" {
		return fmt.Errorf("username is required")
	}

	if request.TemporaryDatabaseName != "" &&
		!isSafeRestoreVerifyDatabaseName(request.TemporaryDatabaseName) {
		return fmt.Errorf(
			"temporaryDatabaseName contains unsafe characters: %s",
			request.TemporaryDatabaseName,
		)
	}

	return nil
}

func buildTemporaryDatabaseName(
	sourceDatabaseName string,
	operationJobID int64,
	now time.Time,
) string {
	safeSourceDatabaseName :=
		sanitizeRestoreVerifyName(sourceDatabaseName)

	return fmt.Sprintf(
		"restore_verify_%s_%d_%s",
		safeSourceDatabaseName,
		operationJobID,
		now.Format("20060102_150405"),
	)
}

func sanitizeRestoreVerifyName(
	value string,
) string {
	var builder strings.Builder

	for _, r := range value {
		if r >= 'a' && r <= 'z' {
			builder.WriteRune(r)
			continue
		}

		if r >= 'A' && r <= 'Z' {
			builder.WriteRune(r)
			continue
		}

		if r >= '0' && r <= '9' {
			builder.WriteRune(r)
			continue
		}

		if r == '_' {
			builder.WriteRune(r)
			continue
		}

		builder.WriteRune('_')
	}

	result :=
		builder.String()

	if result == "" {
		return "database"
	}

	return result
}

func isSafeRestoreVerifyDatabaseName(
	value string,
) bool {
	if value == "" {
		return false
	}

	matched, err :=
		regexp.MatchString(
			`^[a-zA-Z0-9_]+$`,
			value,
		)

	if err != nil {
		return false
	}

	return matched
}