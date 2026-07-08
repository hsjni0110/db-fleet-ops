package restore

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"
	"time"
)

type MySQLRestoreRequest struct {
	Host                  string
	Port                  int
	Username              string
	Password              string
	BackupFile            string
	TemporaryDatabaseName string
	Cleanup               bool
}

type MySQLRestoreResult struct {
	Status                string `json:"status"`
	BackupFile            string `json:"backupFile"`
	TemporaryDatabaseName string `json:"temporaryDatabaseName"`
	StartedAt             string `json:"startedAt"`
	CompletedAt           string `json:"completedAt"`
	Message               string `json:"message"`
	ErrorCode             string `json:"errorCode,omitempty"`
	ErrorMessage          string `json:"errorMessage,omitempty"`
}

type CommandRunner interface {
	Run(
		ctx context.Context,
		name string,
		args []string,
		stdin io.Reader,
		stdout io.Writer,
		stderr io.Writer,
	) error
}

type ExecCommandRunner struct {
}

func (r ExecCommandRunner) Run(
	ctx context.Context,
	name string,
	args []string,
	stdin io.Reader,
	stdout io.Writer,
	stderr io.Writer,
) error {
	command :=
		exec.CommandContext(
			ctx,
			name,
			args...,
		)

	command.Stdin = stdin
	command.Stdout = stdout
	command.Stderr = stderr

	return command.Run()
}

type MySQLRestoreRunner struct {
	commandRunner CommandRunner
}

func NewMySQLRestoreRunner() *MySQLRestoreRunner {
	return &MySQLRestoreRunner{
		commandRunner: ExecCommandRunner{},
	}
}

func NewMySQLRestoreRunnerWithCommandRunner(
	commandRunner CommandRunner,
) *MySQLRestoreRunner {
	return &MySQLRestoreRunner{
		commandRunner: commandRunner,
	}
}

func (r *MySQLRestoreRunner) Run(
	ctx context.Context,
	request MySQLRestoreRequest,
) (MySQLRestoreResult, error) {
	if err := validateMySQLRestoreRequest(request); err != nil {
		return MySQLRestoreResult{}, err
	}

	startedAt :=
		time.Now()

	defaultsFile, err :=
		createDefaultsFile(request)

	if err != nil {
		return MySQLRestoreResult{}, err
	}

	defer os.Remove(defaultsFile)

	if err := r.createTemporaryDatabase(
		ctx,
		defaultsFile,
		request.TemporaryDatabaseName,
	); err != nil {
		return MySQLRestoreResult{}, fmt.Errorf(
			"failed to create temporary database: %w",
			err,
		)
	}

	restoreErr :=
		r.restoreBackupFile(
			ctx,
			defaultsFile,
			request.BackupFile,
			request.TemporaryDatabaseName,
		)

	if restoreErr != nil {
		if request.Cleanup {
			_ = r.dropTemporaryDatabase(
				ctx,
				defaultsFile,
				request.TemporaryDatabaseName,
			)
		}

		return MySQLRestoreResult{
				Status:                "FAILED",
				BackupFile:            request.BackupFile,
				TemporaryDatabaseName: request.TemporaryDatabaseName,
				StartedAt:             startedAt.Format(time.RFC3339),
				CompletedAt:           time.Now().Format(time.RFC3339),
				ErrorCode:             "RESTORE_FAILED",
				ErrorMessage:          restoreErr.Error(),
			},
			restoreErr
	}

	if request.Cleanup {
		if err := r.dropTemporaryDatabase(
			ctx,
			defaultsFile,
			request.TemporaryDatabaseName,
		); err != nil {
			return MySQLRestoreResult{
					Status:                "CLEANUP_FAILED",
					BackupFile:            request.BackupFile,
					TemporaryDatabaseName: request.TemporaryDatabaseName,
					StartedAt:             startedAt.Format(time.RFC3339),
					CompletedAt:           time.Now().Format(time.RFC3339),
					Message:               "restore completed but cleanup failed",
					ErrorCode:             "CLEANUP_FAILED",
					ErrorMessage:          err.Error(),
				},
				err
		}
	}

	return MySQLRestoreResult{
		Status:                "RESTORED",
		BackupFile:            request.BackupFile,
		TemporaryDatabaseName: request.TemporaryDatabaseName,
		StartedAt:             startedAt.Format(time.RFC3339),
		CompletedAt:           time.Now().Format(time.RFC3339),
		Message:               "mysql backup restored to temporary database",
	}, nil
}

func (r *MySQLRestoreRunner) createTemporaryDatabase(
	ctx context.Context,
	defaultsFile string,
	temporaryDatabaseName string,
) error {
	sql :=
		fmt.Sprintf(
			"CREATE DATABASE `%s`",
			temporaryDatabaseName,
		)

	var stderr strings.Builder

	err :=
		r.commandRunner.Run(
			ctx,
			"mysql",
			[]string{
				"--defaults-extra-file=" + defaultsFile,
				"--execute",
				sql,
			},
			nil,
			io.Discard,
			&stderr,
		)

	if err != nil {
		return fmt.Errorf(
			"%w, stderr=%s",
			err,
			stderr.String(),
		)
	}

	return nil
}

func (r *MySQLRestoreRunner) restoreBackupFile(
	ctx context.Context,
	defaultsFile string,
	backupFile string,
	temporaryDatabaseName string,
) error {
	file, err :=
		os.Open(backupFile)

	if err != nil {
		return err
	}

	defer file.Close()

	var stderr strings.Builder

	err =
		r.commandRunner.Run(
			ctx,
			"mysql",
			[]string{
				"--defaults-extra-file=" + defaultsFile,
				temporaryDatabaseName,
			},
			file,
			io.Discard,
			&stderr,
		)

	if err != nil {
		return fmt.Errorf(
			"%w, stderr=%s",
			err,
			stderr.String(),
		)
	}

	return nil
}

func (r *MySQLRestoreRunner) dropTemporaryDatabase(
	ctx context.Context,
	defaultsFile string,
	temporaryDatabaseName string,
) error {
	sql :=
		fmt.Sprintf(
			"DROP DATABASE `%s`",
			temporaryDatabaseName,
		)

	var stderr strings.Builder

	err :=
		r.commandRunner.Run(
			ctx,
			"mysql",
			[]string{
				"--defaults-extra-file=" + defaultsFile,
				"--execute",
				sql,
			},
			nil,
			io.Discard,
			&stderr,
		)

	if err != nil {
		return fmt.Errorf(
			"%w, stderr=%s",
			err,
			stderr.String(),
		)
	}

	return nil
}

func createDefaultsFile(
	request MySQLRestoreRequest,
) (string, error) {
	tempFile, err :=
		os.CreateTemp(
			"",
			"db-fleetops-mysql-restore-*.cnf",
		)

	if err != nil {
		return "", err
	}

	content :=
		fmt.Sprintf(
			"[client]\nhost=%s\nport=%d\nuser=%s\npassword=%s\n",
			request.Host,
			request.Port,
			request.Username,
			request.Password,
		)

	if _, err := tempFile.WriteString(content); err != nil {
		tempFile.Close()
		os.Remove(tempFile.Name())

		return "", err
	}

	if err := tempFile.Chmod(0600); err != nil {
		tempFile.Close()
		os.Remove(tempFile.Name())

		return "", err
	}

	if err := tempFile.Close(); err != nil {
		os.Remove(tempFile.Name())

		return "", err
	}

	return tempFile.Name(), nil
}

func validateMySQLRestoreRequest(
	request MySQLRestoreRequest,
) error {
	if request.Host == "" {
		return fmt.Errorf("host is required")
	}

	if request.Port <= 0 {
		return fmt.Errorf("port is required")
	}

	if request.Username == "" {
		return fmt.Errorf("username is required")
	}

	if request.BackupFile == "" {
		return fmt.Errorf("backupFile is required")
	}

	if request.TemporaryDatabaseName == "" {
		return fmt.Errorf("temporaryDatabaseName is required")
	}

	if !isSafeDatabaseName(request.TemporaryDatabaseName) {
		return fmt.Errorf(
			"temporaryDatabaseName contains unsafe characters: %s",
			request.TemporaryDatabaseName,
		)
	}

	fileInfo, err :=
		os.Stat(request.BackupFile)

	if err != nil {
		return err
	}

	if fileInfo.IsDir() {
		return fmt.Errorf(
			"backupFile is directory: %s",
			request.BackupFile,
		)
	}

	if fileInfo.Size() <= 0 {
		return fmt.Errorf(
			"backupFile is empty: %s",
			request.BackupFile,
		)
	}

	return nil
}

func isSafeDatabaseName(
	value string,
) bool {
	if value == "" {
		return false
	}

	for _, r := range value {
		if r >= 'a' && r <= 'z' {
			continue
		}

		if r >= 'A' && r <= 'Z' {
			continue
		}

		if r >= '0' && r <= '9' {
			continue
		}

		if r == '_' {
			continue
		}

		return false
	}

	return true
}

func SanitizeDatabaseName(
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
		return "restore_verify_database"
	}

	return result
}