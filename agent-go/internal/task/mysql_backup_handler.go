package task

import (
	"context"
	"encoding/json"
	"time"

	"db-fleetops-agent/internal/port"
)

const TaskTypeMySQLLogicalBackup = "MYSQL_LOGICAL_BACKUP"

type MySQLBackupRequest struct {
	DatabaseName string `json:"databaseName"`
	BackupType   string `json:"backupType"`
	Compression  bool   `json:"compression"`
}

type MySQLBackupResult struct {
	Status     string `json:"status"`
	BackupFile string `json:"backupFile"`
	CreatedAt  string `json:"createdAt"`
	Message    string `json:"message"`
}

type MySQLBackupHandler struct {
	backupDirectory string
}

func NewMySQLBackupHandler(
	backupDirectory string,
) *MySQLBackupHandler {
	return &MySQLBackupHandler{
		backupDirectory: backupDirectory,
	}
}

func (h *MySQLBackupHandler) Supports(
	taskType string,
) bool {
	return taskType == TaskTypeMySQLLogicalBackup
}

func (h *MySQLBackupHandler) Handle(
	ctx context.Context,
	task port.Task,
) (string, error) {
	var request MySQLBackupRequest

	if task.ParametersJSON != "" {
		if err := json.Unmarshal(
			[]byte(task.ParametersJSON),
			&request,
		); err != nil {
			return "", err
		}
	}

	if request.DatabaseName == "" {
		request.DatabaseName = "unknown"
	}

	now := time.Now()

	result := MySQLBackupResult{
		Status: "CREATED",
		BackupFile: h.backupDirectory +
			"/" +
			request.DatabaseName +
			"-" +
			now.Format("20060102-150405") +
			".sql",
		CreatedAt: now.Format(time.RFC3339),
		Message:   "stub mysql logical backup completed",
	}

	resultBytes, err := json.Marshal(result)

	if err != nil {
		return "", err
	}

	return string(resultBytes), nil
}