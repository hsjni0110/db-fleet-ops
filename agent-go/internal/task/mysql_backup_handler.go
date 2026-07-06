package task

import (
	"context"
	"encoding/json"

	"db-fleetops-agent/internal/infra/backup"
	"db-fleetops-agent/internal/port"
)

const TaskTypeMySQLLogicalBackup = "MYSQL_LOGICAL_BACKUP"

type MySQLBackupRequest struct {
	DatabaseName string `json:"databaseName"`
	Host         string `json:"host"`
	Port         int    `json:"port"`
	Username     string `json:"username"`
	Password     string `json:"password"`
	BackupType   string `json:"backupType"`
	Compression  bool   `json:"compression"`
}

type MySQLBackupRunner interface {
	Run(
		ctx context.Context,
		request backup.MySQLDumpRequest,
	) (backup.MySQLDumpResult, error)
}

type MySQLBackupHandler struct {
	backupDirectory string
	runner          MySQLBackupRunner
}

func NewMySQLBackupHandler(
	backupDirectory string,
) *MySQLBackupHandler {
	return &MySQLBackupHandler{
		backupDirectory: backupDirectory,
		runner:          backup.NewMySQLDumpRunner(),
	}
}

func NewMySQLBackupHandlerWithRunner(
	backupDirectory string,
	runner MySQLBackupRunner,
) *MySQLBackupHandler {
	return &MySQLBackupHandler{
		backupDirectory: backupDirectory,
		runner:          runner,
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

	if err := json.Unmarshal(
		[]byte(task.ParametersJSON),
		&request,
	); err != nil {
		return "", err
	}

	result, err :=
		h.runner.Run(
			ctx,
			backup.MySQLDumpRequest{
				Host:            request.Host,
				Port:            request.Port,
				Username:        request.Username,
				Password:        request.Password,
				DatabaseName:    request.DatabaseName,
				BackupDirectory: h.backupDirectory,
				Compression:     request.Compression,
			},
		)

	if err != nil {
		return "", err
	}

	resultBytes, err :=
		json.Marshal(result)

	if err != nil {
		return "", err
	}

	return string(resultBytes), nil
}