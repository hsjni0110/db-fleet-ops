package backup

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"strings"
)

type BackupVerificationResult struct {
	Valid          bool   `json:"valid"`
	FileSizeBytes  int64  `json:"fileSizeBytes"`
	ChecksumSHA256 string `json:"checksumSha256"`
	Message        string `json:"message"`
}

type BackupVerifier struct {
}

func NewBackupVerifier() *BackupVerifier {
	return &BackupVerifier{}
}

func (v *BackupVerifier) Verify(
	ctx context.Context,
	backupFile string,
) (BackupVerificationResult, error) {
	select {
	case <-ctx.Done():
		return BackupVerificationResult{}, ctx.Err()
	default:
	}

	fileInfo, err := os.Stat(backupFile)

	if err != nil {
		return BackupVerificationResult{}, err
	}

	if fileInfo.Size() == 0 {
		return BackupVerificationResult{
			Valid:         false,
			FileSizeBytes: 0,
			Message:       "backup file is empty",
		}, nil
	}

	checksum, err := checksumSHA256(backupFile)

	if err != nil {
		return BackupVerificationResult{}, err
	}

	if !hasMySQLDumpHeader(backupFile) {
		return BackupVerificationResult{
			Valid:          false,
			FileSizeBytes:  fileInfo.Size(),
			ChecksumSHA256: checksum,
			Message:        "backup file does not look like mysql dump output",
		}, nil
	}

	return BackupVerificationResult{
		Valid:          true,
		FileSizeBytes:  fileInfo.Size(),
		ChecksumSHA256: checksum,
		Message:        "backup artifact verified",
	}, nil
}

func hasMySQLDumpHeader(
	filePath string,
) bool {
	file, err := os.Open(filePath)

	if err != nil {
		return false
	}

	defer file.Close()

	scanner := bufio.NewScanner(file)

	lineCount := 0

	for scanner.Scan() {
		line := scanner.Text()

		if strings.Contains(line, "MySQL dump") ||
			strings.Contains(line, "MariaDB dump") ||
			strings.Contains(line, "Host:") ||
			strings.Contains(line, "Server version") {
			return true
		}

		lineCount++

		if lineCount >= 20 {
			break
		}
	}

	return false
}

func VerifyBackupOrError(
	ctx context.Context,
	backupFile string,
) (BackupVerificationResult, error) {
	verifier := NewBackupVerifier()

	result, err :=
		verifier.Verify(
			ctx,
			backupFile,
		)

	if err != nil {
		return BackupVerificationResult{}, err
	}

	if !result.Valid {
		return result, fmt.Errorf(
			"backup verification failed: %s",
			result.Message,
		)
	}

	return result, nil
}