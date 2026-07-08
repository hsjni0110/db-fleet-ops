package backup

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

type MySQLDumpRequest struct {
	Host            string
	Port            int
	Username        string
	Password        string
	DatabaseName    string
	BackupDirectory string
	Compression     bool
}

type MySQLDumpResult struct {
	Status         string `json:"status"`
	BackupFile     string `json:"backupFile"`
	FileSizeBytes  int64  `json:"fileSizeBytes"`
	ChecksumSHA256 string `json:"checksumSha256"`
	CreatedAt      string `json:"createdAt"`
	Message        string `json:"message"`
}

type MySQLDumpRunner struct {
}

func NewMySQLDumpRunner() *MySQLDumpRunner {
	return &MySQLDumpRunner{}
}

func (r *MySQLDumpRunner) Run(
	ctx context.Context,
	request MySQLDumpRequest,
) (MySQLDumpResult, error) {
	if request.Host == "" {
		return MySQLDumpResult{}, fmt.Errorf("host is required")
	}

	if request.Port <= 0 {
		return MySQLDumpResult{}, fmt.Errorf("port is required")
	}

	if request.Username == "" {
		return MySQLDumpResult{}, fmt.Errorf("username is required")
	}

	if request.DatabaseName == "" {
		return MySQLDumpResult{}, fmt.Errorf("databaseName is required")
	}

	if request.BackupDirectory == "" {
		return MySQLDumpResult{}, fmt.Errorf("backupDirectory is required")
	}

	if err := os.MkdirAll(
		request.BackupDirectory,
		0700,
	); err != nil {
		return MySQLDumpResult{}, err
	}

	timestamp :=
		time.Now().Format("20060102-150405")

	backupFile :=
		filepath.Join(
			request.BackupDirectory,
			sanitizeFileName(request.DatabaseName)+"-"+timestamp+".sql",
		)

	defaultsFile, err :=
		createDefaultsFile(request)

	if err != nil {
		return MySQLDumpResult{}, err
	}

	defer os.Remove(defaultsFile)

	outputFile, err :=
		os.Create(backupFile)

	if err != nil {
		return MySQLDumpResult{}, err
	}

	defer outputFile.Close()

	command :=
		exec.CommandContext(
			ctx,
			"mysqldump",
			"--defaults-extra-file="+defaultsFile,
			"--single-transaction",
			"--quick",
			"--routines",
			"--triggers",
			"--events",
			"--set-gtid-purged=OFF",
			request.DatabaseName,
		)

	command.Stdout = outputFile

	if commandError := command.Run(); commandError != nil {
		return MySQLDumpResult{}, commandError
	}

	verificationResult, err :=
		VerifyBackupOrError(
			ctx,
			backupFile,
		)

	if err != nil {
		return MySQLDumpResult{}, err
	}

	return MySQLDumpResult{
		Status:         "VERIFIED",
		BackupFile:     backupFile,
		FileSizeBytes:  verificationResult.FileSizeBytes,
		ChecksumSHA256: verificationResult.ChecksumSHA256,
		CreatedAt:      time.Now().Format(time.RFC3339),
		Message:        verificationResult.Message,
	}, nil
}

func createDefaultsFile(
	request MySQLDumpRequest,
) (string, error) {
	tempFile, err :=
		os.CreateTemp(
			"",
			"db-fleetops-mysql-*.cnf",
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

func checksumSHA256(
	filePath string,
) (string, error) {
	fileBytes, err :=
		os.ReadFile(filePath)

	if err != nil {
		return "", err
	}

	sum :=
		sha256.Sum256(fileBytes)

	return hex.EncodeToString(sum[:]), nil
}

func sanitizeFileName(
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

		if r == '_' || r == '-' {
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