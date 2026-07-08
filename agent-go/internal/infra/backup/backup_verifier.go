package backup

import (
	"context"
	"fmt"
	"os"
	"strings"
)

type BackupVerificationResult struct {
	Status         string `json:"status"`
	BackupFile     string `json:"backupFile"`
	FileSizeBytes  int64  `json:"fileSizeBytes"`
	ChecksumSHA256 string `json:"checksumSha256"`
	Message        string `json:"message"`
}

func VerifyBackupOrError(
	ctx context.Context,
	backupFile string,
) (BackupVerificationResult, error) {
	if backupFile == "" {
		return BackupVerificationResult{}, fmt.Errorf("backupFile is required")
	}

	select {
	case <-ctx.Done():
		return BackupVerificationResult{}, ctx.Err()
	default:
	}

	fileInfo, err :=
		os.Stat(backupFile)

	if err != nil {
		return BackupVerificationResult{}, err
	}

	if fileInfo.IsDir() {
		return BackupVerificationResult{}, fmt.Errorf(
			"backup file is directory: %s",
			backupFile,
		)
	}

	if fileInfo.Size() <= 0 {
		return BackupVerificationResult{}, fmt.Errorf(
			"backup file is empty: %s",
			backupFile,
		)
	}

	fileBytes, err :=
		os.ReadFile(backupFile)

	if err != nil {
		return BackupVerificationResult{}, err
	}

	content :=
		string(fileBytes)

	if !looksLikeMySQLDump(content) {
		return BackupVerificationResult{}, fmt.Errorf(
			"backup file does not look like mysql dump: %s",
			backupFile,
		)
	}

	checksum, err :=
		checksumSHA256(backupFile)

	if err != nil {
		return BackupVerificationResult{}, err
	}

	return BackupVerificationResult{
		Status:         "VERIFIED",
		BackupFile:     backupFile,
		FileSizeBytes:  fileInfo.Size(),
		ChecksumSHA256: checksum,
		Message:        "backup artifact verified",
	}, nil
}

func looksLikeMySQLDump(
	content string,
) bool {
	normalized :=
		strings.ToUpper(content)

	if strings.Contains(normalized, "MYSQL DUMP") {
		return true
	}

	if strings.Contains(normalized, "CREATE TABLE") {
		return true
	}

	if strings.Contains(normalized, "INSERT INTO") {
		return true
	}

	if strings.Contains(normalized, "DROP TABLE") {
		return true
	}

	if strings.Contains(normalized, "LOCK TABLES") {
		return true
	}

	return false
}