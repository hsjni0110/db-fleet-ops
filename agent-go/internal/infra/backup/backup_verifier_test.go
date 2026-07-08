package backup

import (
	"context"
	"os"
	"path/filepath"
	"testing"
)

func TestVerifyBackupOrErrorAcceptsDumpWithoutCreateDatabase(t *testing.T) {
	tempDirectory :=
		t.TempDir()

	backupFile :=
		filepath.Join(
			tempDirectory,
			"orders.sql",
		)

	content :=
		"-- MySQL dump\n" +
			"DROP TABLE IF EXISTS `orders`;\n" +
			"CREATE TABLE `orders` (`id` bigint);\n" +
			"INSERT INTO `orders` VALUES (1);\n"

	if err := os.WriteFile(
		backupFile,
		[]byte(content),
		0600,
	); err != nil {
		t.Fatalf("failed to write backup file: %v", err)
	}

	result, err :=
		VerifyBackupOrError(
			context.Background(),
			backupFile,
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.Status != "VERIFIED" {
		t.Fatalf("expected VERIFIED, got %s", result.Status)
	}

	if result.FileSizeBytes <= 0 {
		t.Fatalf("expected positive file size")
	}

	if result.ChecksumSHA256 == "" {
		t.Fatalf("expected checksum")
	}
}

func TestVerifyBackupOrErrorRejectsEmptyFile(t *testing.T) {
	tempDirectory :=
		t.TempDir()

	backupFile :=
		filepath.Join(
			tempDirectory,
			"empty.sql",
		)

	if err := os.WriteFile(
		backupFile,
		[]byte(""),
		0600,
	); err != nil {
		t.Fatalf("failed to write backup file: %v", err)
	}

	_, err :=
		VerifyBackupOrError(
			context.Background(),
			backupFile,
		)

	if err == nil {
		t.Fatalf("expected error")
	}
}

func TestVerifyBackupOrErrorRejectsNonDumpFile(t *testing.T) {
	tempDirectory :=
		t.TempDir()

	backupFile :=
		filepath.Join(
			tempDirectory,
			"not-dump.sql",
		)

	if err := os.WriteFile(
		backupFile,
		[]byte("hello world"),
		0600,
	); err != nil {
		t.Fatalf("failed to write backup file: %v", err)
	}

	_, err :=
		VerifyBackupOrError(
			context.Background(),
			backupFile,
		)

	if err == nil {
		t.Fatalf("expected error")
	}
}