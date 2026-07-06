package backup

import (
	"os"
	"strings"
	"testing"
)

func TestCreateDefaultsFileDoesNotExposePasswordInFileName(t *testing.T) {
	filePath, err :=
		createDefaultsFile(
			MySQLDumpRequest{
				Host:         "127.0.0.1",
				Port:         3306,
				Username:     "backup_user",
				Password:     "secret-password",
				DatabaseName: "orders",
			},
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	defer os.Remove(filePath)

	if strings.Contains(filePath, "secret-password") {
		t.Fatal("expected file path not to contain password")
	}

	fileBytes, err :=
		os.ReadFile(filePath)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	content :=
		string(fileBytes)

	if !strings.Contains(content, "password=secret-password") {
		t.Fatal("expected defaults file to contain password")
	}
}

func TestChecksumSHA256(t *testing.T) {
	tempFile, err :=
		os.CreateTemp(
			"",
			"db-fleetops-checksum-*.sql",
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	defer os.Remove(tempFile.Name())

	if _, err := tempFile.WriteString("hello"); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if err := tempFile.Close(); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	checksum, err :=
		checksumSHA256(tempFile.Name())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	expected :=
		"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

	if checksum != expected {
		t.Fatalf(
			"expected checksum %s, got %s",
			expected,
			checksum,
		)
	}
}