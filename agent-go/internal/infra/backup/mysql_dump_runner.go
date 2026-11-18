package backup

import (
	"bufio"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
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
	Status         string          `json:"status"`
	BackupFile     string          `json:"backupFile"`
	FileSizeBytes  int64           `json:"fileSizeBytes"`
	ChecksumSHA256 string          `json:"checksumSha256"`
	CreatedAt      string          `json:"createdAt"`
	Message        string          `json:"message"`
	BinlogPosition *BinlogPosition `json:"binlogPosition,omitempty"`
}

type BinlogPosition struct {
	File     string `json:"file"`
	Position uint64 `json:"position"`
}

type MySQLDumpRunner struct {
	captureBinlogPosition bool
}

func NewMySQLDumpRunner(captureBinlogPosition bool) *MySQLDumpRunner {
	return &MySQLDumpRunner{captureBinlogPosition: captureBinlogPosition}
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

	args := []string{
		"--defaults-extra-file=" + defaultsFile,
		"--single-transaction",
		"--quick",
		"--routines",
		"--triggers",
		"--events",
		"--set-gtid-purged=OFF",
	}
	if r.captureBinlogPosition {
		args = append(args, "--source-data=2")
	}
	command := exec.CommandContext(ctx, "mysqldump", append(args, request.DatabaseName)...)

	command.Stdout = outputFile

	if commandError := command.Run(); commandError != nil {
		return MySQLDumpResult{}, commandError
	}

	var position *BinlogPosition
	if r.captureBinlogPosition {
		position, err = readBinlogPosition(backupFile)
		if err != nil {
			return MySQLDumpResult{}, err
		}
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
		BinlogPosition: position,
	}, nil
}

var binlogPositionComment = regexp.MustCompile(`^-- CHANGE (?:REPLICATION SOURCE TO SOURCE_LOG_FILE|MASTER TO MASTER_LOG_FILE)='([A-Za-z0-9_.-]+)', +(?:SOURCE|MASTER)_LOG_POS=([0-9]+);$`)

func readBinlogPosition(path string) (*BinlogPosition, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "-- Table structure") || strings.HasPrefix(line, "CREATE ") {
			break
		}
		match := binlogPositionComment.FindStringSubmatch(line)
		if match == nil {
			continue
		}
		position, err := strconv.ParseUint(match[2], 10, 53)
		if err != nil || position < 4 {
			return nil, fmt.Errorf("invalid binary log position in dump header")
		}
		return &BinlogPosition{File: match[1], Position: position}, nil
	}
	if err := scanner.Err(); err != nil {
		return nil, err
	}
	return nil, fmt.Errorf("binary log position is missing from dump header")
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
