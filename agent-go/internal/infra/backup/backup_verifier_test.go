package backup

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestBackupVerifierReturnsValidForMySQLDumpFile(t *testing.T) {
	tempDir := t.TempDir()

	backupFile :=
		filepath.Join(
			tempDir,
			"orders.sql",
		)

	content := `-- MySQL dump 10.13  Distrib 8.0.36
-- Host: 127.0.0.1    Database: orders
CREATE DATABASE /*!32312 IF NOT EXISTS*/ orders;
`

	if err := os.WriteFile(
		backupFile,
		[]byte(content),
		0600,
	); err != nil {
		t.Fatalf("failed to write backup file: %v", err)
	}

	verifier := NewBackupVerifier()

	result, err :=
		verifier.Verify(
			context.Background(),
			backupFile,
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !result.Valid {
		t.Fatalf("expected valid backup, got invalid: %s", result.Message)
	}

	if result.FileSizeBytes == 0 {
		t.Fatal("expected file size to be greater than zero")
	}

	if result.ChecksumSHA256 == "" {
		t.Fatal("expected checksum to be generated")
	}
}

func TestBackupVerifierReturnsInvalidForEmptyFile(t *testing.T) {
	tempDir := t.TempDir()

	backupFile :=
		filepath.Join(
			tempDir,
			"empty.sql",
		)

	if err := os.WriteFile(
		backupFile,
		[]byte(""),
		0600,
	); err != nil {
		t.Fatalf("failed to write backup file: %v", err)
	}

	verifier := NewBackupVerifier()

	result, err :=
		verifier.Verify(
			context.Background(),
			backupFile,
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.Valid {
		t.Fatal("expected empty backup file to be invalid")
	}

	if result.Message != "backup file is empty" {
		t.Fatalf("unexpected message: %s", result.Message)
	}
}

func TestBackupVerifierReturnsErrorForMissingFile(t *testing.T) {
	verifier := NewBackupVerifier()

	_, err :=
		verifier.Verify(
			context.Background(),
			"/tmp/not-exists-db-fleetops.sql",
		)

	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestBackupVerifierReturnsInvalidForNonDumpTextFile(t *testing.T) {
	tempDir := t.TempDir()

	backupFile :=
		filepath.Join(
			tempDir,
			"random.sql",
		)

	if err := os.WriteFile(
		backupFile,
		[]byte("hello world"),
		0600,
	); err != nil {
		t.Fatalf("failed to write backup file: %v", err)
	}

	verifier := NewBackupVerifier()

	result, err :=
		verifier.Verify(
			context.Background(),
			backupFile,
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.Valid {
		t.Fatal("expected non dump text file to be invalid")
	}

	if !strings.Contains(result.Message, "mysql dump") {
		t.Fatalf("unexpected message: %s", result.Message)
	}
}

func TestVerifyBackupOrErrorReturnsErrorWhenBackupIsInvalid(t *testing.T) {
	tempDir := t.TempDir()

	backupFile :=
		filepath.Join(
			tempDir,
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
		t.Fatal("expected error, got nil")
	}
}