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
	OperationJobID                 int64                  `json:"operationJobId"`
	DatabaseID                     int64                  `json:"databaseId"`
	BackupTaskID                   int64                  `json:"backupTaskId"`
	SourceDatabaseName             string                 `json:"sourceDatabaseName"`
	BackupFile                     string                 `json:"backupFile"`
	ExpectedChecksumSHA256         string                 `json:"expectedChecksumSha256"`
	Host                           string                 `json:"host"`
	Port                           int                    `json:"port"`
	Username                       string                 `json:"username"`
	Password                       string                 `json:"password"`
	TemporaryDatabaseName          string                 `json:"temporaryDatabaseName"`
	ExpectedTables                 []string               `json:"expectedTables"`
	VerifyRowCount                 bool                   `json:"verifyRowCount"`
	Cleanup                        bool                   `json:"cleanup"`
	BusinessVerificationProfileKey string                 `json:"businessVerificationProfileKey"`
	BusinessRules                  []restore.BusinessRule `json:"businessRules"`
}

type MySQLRestoreVerifyResult struct {
	Status                string                                 `json:"status"`
	OperationJobID        int64                                  `json:"operationJobId"`
	DatabaseID            int64                                  `json:"databaseId"`
	BackupTaskID          int64                                  `json:"backupTaskId"`
	SourceDatabaseName    string                                 `json:"sourceDatabaseName"`
	BackupFile            string                                 `json:"backupFile"`
	TemporaryDatabaseName string                                 `json:"temporaryDatabaseName"`
	RestoredTableCount    int                                    `json:"restoredTableCount"`
	CheckedTableCount     int                                    `json:"checkedTableCount"`
	TotalRowCount         int64                                  `json:"totalRowCount"`
	StartedAt             string                                 `json:"startedAt"`
	CompletedAt           string                                 `json:"completedAt"`
	Items                 []restore.MySQLRestoreVerifyItemResult `json:"items"`
	Message               string                                 `json:"message"`
	ErrorCode             string                                 `json:"errorCode,omitempty"`
	ErrorMessage          string                                 `json:"errorMessage,omitempty"`
	TechnicalStatus       string                                 `json:"technicalStatus"`
	BusinessVerification  restore.MySQLBusinessVerifyResult      `json:"businessVerification"`
	CleanupStatus         string                                 `json:"cleanupStatus"`
	CleanupErrorCode      string                                 `json:"cleanupErrorCode,omitempty"`
	CleanupErrorMessage   string                                 `json:"cleanupErrorMessage,omitempty"`
}

type MySQLRestoreRunner interface {
	Run(context.Context, restore.MySQLRestoreRequest) (restore.MySQLRestoreResult, error)
	Cleanup(context.Context, restore.MySQLRestoreCleanupRequest) error
}

type MySQLRestoreVerifier interface {
	Verify(context.Context, restore.MySQLRestoreVerifyRequest) (restore.MySQLRestoreVerifyResult, error)
}

type MySQLBusinessVerifier interface {
	Verify(context.Context, restore.MySQLBusinessVerifyRequest) restore.MySQLBusinessVerifyResult
}

type MySQLRestoreVerifyHandler struct {
	runner           MySQLRestoreRunner
	verifier         MySQLRestoreVerifier
	businessVerifier MySQLBusinessVerifier
}

func NewMySQLRestoreVerifyHandler() *MySQLRestoreVerifyHandler {
	return NewMySQLRestoreVerifyHandlerWithAllDependencies(restore.NewMySQLRestoreRunner(),
		restore.NewMySQLRestoreVerifier(), restore.NewMySQLBusinessVerifier())
}

func NewMySQLRestoreVerifyHandlerWithDependencies(runner MySQLRestoreRunner,
	verifier MySQLRestoreVerifier) *MySQLRestoreVerifyHandler {
	return NewMySQLRestoreVerifyHandlerWithAllDependencies(runner, verifier,
		restore.NewMySQLBusinessVerifier())
}

func NewMySQLRestoreVerifyHandlerWithAllDependencies(runner MySQLRestoreRunner,
	verifier MySQLRestoreVerifier, businessVerifier MySQLBusinessVerifier) *MySQLRestoreVerifyHandler {
	return &MySQLRestoreVerifyHandler{runner: runner, verifier: verifier,
		businessVerifier: businessVerifier}
}

func (h *MySQLRestoreVerifyHandler) Supports(taskType string) bool {
	return taskType == TaskTypeMySQLRestoreVerify
}

func (h *MySQLRestoreVerifyHandler) Handle(ctx context.Context, task port.Task) (string, error) {
	startedAt := time.Now()
	var request MySQLRestoreVerifyRequest
	if err := json.Unmarshal([]byte(task.ParametersJSON), &request); err != nil {
		return "", err
	}
	if err := validateMySQLRestoreVerifyTaskRequest(request); err != nil {
		return "", err
	}

	temporaryDatabaseName := request.TemporaryDatabaseName
	if temporaryDatabaseName == "" {
		temporaryDatabaseName = buildTemporaryDatabaseName(request.SourceDatabaseName,
			request.OperationJobID, startedAt)
	}

	restoreResult, restoreErr := h.runner.Run(ctx, restore.MySQLRestoreRequest{
		Host: request.Host, Port: request.Port, Username: request.Username,
		Password: request.Password, BackupFile: request.BackupFile,
		ExpectedChecksumSHA256: request.ExpectedChecksumSHA256,
		TemporaryDatabaseName:  temporaryDatabaseName, Cleanup: false,
	})

	verifyResult := restore.MySQLRestoreVerifyResult{}
	technicalStatus := "FAILED"
	if restoreErr == nil {
		var verifyErr error
		verifyResult, verifyErr = h.verifier.Verify(ctx, restore.MySQLRestoreVerifyRequest{
			Host: request.Host, Port: request.Port, Username: request.Username,
			Password: request.Password, TemporaryDatabaseName: temporaryDatabaseName,
			ExpectedTables: request.ExpectedTables, VerifyRowCount: request.VerifyRowCount,
		})
		if verifyErr == nil && strings.EqualFold(verifyResult.Status, "VERIFIED") {
			technicalStatus = "VERIFIED"
		}
	}

	businessResult := restore.MySQLBusinessVerifyResult{Status: "NOT_APPLIED",
		RuleResults: []restore.BusinessRuleResult{}}
	if request.BusinessVerificationProfileKey != "" {
		if technicalStatus == "VERIFIED" {
			businessResult = h.businessVerifier.Verify(ctx, restore.MySQLBusinessVerifyRequest{
				Host: request.Host, Port: request.Port, Username: request.Username,
				Password: request.Password, TemporaryDatabaseName: temporaryDatabaseName,
				ProfileKey: request.BusinessVerificationProfileKey, Rules: request.BusinessRules,
			})
		} else {
			businessResult = restore.MySQLBusinessVerifyResult{
				ProfileKey: request.BusinessVerificationProfileKey, Status: "SKIPPED",
				RuleResults: []restore.BusinessRuleResult{}}
		}
	}

	var cleanupErr error
	cleanupStatus := "NOT_REQUESTED"
	if request.Cleanup && restoreResult.ErrorCode == restore.BackupIntegrityCheckFailed {
		cleanupStatus = "SKIPPED"
	} else if request.Cleanup {
		cleanupErr = cleanupTemporaryDatabase(ctx, h.runner, request, temporaryDatabaseName)
		cleanupStatus = "COMPLETED"
		if cleanupErr != nil {
			cleanupStatus = "FAILED"
		}
	}

	status, errorCode, errorMessage := decideRestoreVerificationStatus(technicalStatus,
		businessResult.Status, cleanupStatus, restoreResult, verifyResult, restoreErr, cleanupErr)
	result := MySQLRestoreVerifyResult{
		Status: status, OperationJobID: request.OperationJobID, DatabaseID: request.DatabaseID,
		BackupTaskID: request.BackupTaskID, SourceDatabaseName: request.SourceDatabaseName,
		BackupFile: request.BackupFile, TemporaryDatabaseName: temporaryDatabaseName,
		RestoredTableCount: verifyResult.RestoredTableCount,
		CheckedTableCount:  verifyResult.CheckedTableCount, TotalRowCount: verifyResult.TotalRowCount,
		StartedAt: startedAt.Format(time.RFC3339), CompletedAt: time.Now().Format(time.RFC3339),
		Items: verifyResult.Items, Message: "restore verification completed",
		ErrorCode: errorCode, ErrorMessage: errorMessage, TechnicalStatus: technicalStatus,
		BusinessVerification: businessResult, CleanupStatus: cleanupStatus,
	}
	if cleanupErr != nil {
		result.CleanupErrorCode = "CLEANUP_FAILED"
		result.CleanupErrorMessage = cleanupErr.Error()
	}
	resultBytes, err := json.Marshal(result)
	if err != nil {
		return "", err
	}
	return string(resultBytes), nil
}

func decideRestoreVerificationStatus(technicalStatus, businessStatus, cleanupStatus string,
	restoreResult restore.MySQLRestoreResult, verifyResult restore.MySQLRestoreVerifyResult,
	restoreErr, cleanupErr error) (string, string, string) {
	if technicalStatus == "FAILED" {
		code, message := verifyResult.ErrorCode, verifyResult.ErrorMessage
		if restoreErr != nil {
			code, message = restoreResult.ErrorCode, restoreResult.ErrorMessage
		}
		if code == "" {
			code = "RESTORE_VERIFY_FAILED"
		}
		if message == "" {
			message = "technical verification failed"
		}
		return "FAILED", code, message
	}
	if businessStatus == "ERROR" {
		return "FAILED", "BUSINESS_VERIFICATION_ERROR", "one or more Business Rules could not be evaluated"
	}
	if businessStatus == "FAILED" {
		return "FAILED", "BUSINESS_VERIFICATION_FAILED", "one or more Business Rules found violations"
	}
	if cleanupStatus == "FAILED" {
		return "CLEANUP_FAILED", "CLEANUP_FAILED", cleanupErr.Error()
	}
	return "VERIFIED", "", ""
}

func cleanupTemporaryDatabase(ctx context.Context, runner MySQLRestoreRunner,
	request MySQLRestoreVerifyRequest, temporaryDatabaseName string) error {
	return runner.Cleanup(ctx, restore.MySQLRestoreCleanupRequest{
		Host: request.Host, Port: request.Port, Username: request.Username,
		Password: request.Password, BackupFile: request.BackupFile,
		TemporaryDatabaseName: temporaryDatabaseName,
	})
}

func validateMySQLRestoreVerifyTaskRequest(request MySQLRestoreVerifyRequest) error {
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
	if request.TemporaryDatabaseName != "" && !isSafeRestoreVerifyDatabaseName(request.TemporaryDatabaseName) {
		return fmt.Errorf("temporaryDatabaseName contains unsafe characters: %s", request.TemporaryDatabaseName)
	}
	return nil
}

func buildTemporaryDatabaseName(sourceDatabaseName string, operationJobID int64, now time.Time) string {
	return fmt.Sprintf("restore_verify_%s_%d_%s", sanitizeRestoreVerifyName(sourceDatabaseName),
		operationJobID, now.Format("20060102_150405"))
}

func sanitizeRestoreVerifyName(value string) string {
	var builder strings.Builder
	for _, r := range value {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') ||
			(r >= '0' && r <= '9') || r == '_' {
			builder.WriteRune(r)
		} else {
			builder.WriteRune('_')
		}
	}
	if builder.Len() == 0 {
		return "database"
	}
	return builder.String()
}

func isSafeRestoreVerifyDatabaseName(value string) bool {
	matched, err := regexp.MatchString(`^[a-zA-Z0-9_]+$`, value)
	return value != "" && err == nil && matched
}
