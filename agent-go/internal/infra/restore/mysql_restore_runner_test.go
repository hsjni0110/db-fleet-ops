package restore

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

type recordedCommand struct {
	name      string
	args      []string
	stdinData string
}

type fakeCommandRunner struct {
	commands       []recordedCommand
	failOnCommand  int
	failWithError  error
}

func (r *fakeCommandRunner) Run(
	ctx context.Context,
	name string,
	args []string,
	stdin io.Reader,
	stdout io.Writer,
	stderr io.Writer,
) error {
	command :=
		recordedCommand{
			name: name,
			args: append([]string{}, args...),
		}

	if stdin != nil {
		bytes, err :=
			io.ReadAll(stdin)

		if err != nil {
			return err
		}

		command.stdinData =
			string(bytes)
	}

	r.commands =
		append(
			r.commands,
			command,
		)

	if r.failOnCommand > 0 && len(r.commands) == r.failOnCommand {
		if stderr != nil {
			_, _ = stderr.Write(
				[]byte("fake command failure"),
			)
		}

		if r.failWithError != nil {
			return r.failWithError
		}

		return fmt.Errorf("fake command failure")
	}

	return nil
}

func TestMySQLRestoreRunnerRestoresBackupAndCleansUp(t *testing.T) {
	backupFile :=
		createTestBackupFile(t)

	commandRunner :=
		&fakeCommandRunner{}

	runner :=
		NewMySQLRestoreRunnerWithCommandRunner(
			commandRunner,
		)

	result, err :=
		runner.Run(
			context.Background(),
			MySQLRestoreRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				BackupFile:            backupFile,
				TemporaryDatabaseName: "restore_verify_orders_100",
				Cleanup:               true,
			},
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.Status != "RESTORED" {
		t.Fatalf("expected RESTORED, got %s", result.Status)
	}

	if len(commandRunner.commands) != 3 {
		t.Fatalf("expected 3 commands, got %d", len(commandRunner.commands))
	}

	createCommand :=
		commandRunner.commands[0]

	if createCommand.name != "mysql" {
		t.Fatalf("expected mysql command, got %s", createCommand.name)
	}

	if !containsArg(createCommand.args, "--execute") {
		t.Fatalf("expected create database command to use --execute, args=%v", createCommand.args)
	}

	if !containsText(createCommand.args, "CREATE DATABASE `restore_verify_orders_100`") {
		t.Fatalf("expected create database sql, args=%v", createCommand.args)
	}

	restoreCommand :=
		commandRunner.commands[1]

	if restoreCommand.name != "mysql" {
		t.Fatalf("expected mysql command, got %s", restoreCommand.name)
	}

	if restoreCommand.stdinData == "" {
		t.Fatalf("expected backup file content to be passed via stdin")
	}

	if !strings.Contains(restoreCommand.stdinData, "CREATE TABLE") {
		t.Fatalf("expected backup file content in stdin, got %s", restoreCommand.stdinData)
	}

	if !containsArg(restoreCommand.args, "restore_verify_orders_100") {
		t.Fatalf("expected temporary database name as mysql argument, args=%v", restoreCommand.args)
	}

	dropCommand :=
		commandRunner.commands[2]

	if !containsText(dropCommand.args, "DROP DATABASE `restore_verify_orders_100`") {
		t.Fatalf("expected drop database sql, args=%v", dropCommand.args)
	}
}

func TestMySQLRestoreRunnerDoesNotCleanupWhenCleanupFalse(t *testing.T) {
	backupFile :=
		createTestBackupFile(t)

	commandRunner :=
		&fakeCommandRunner{}

	runner :=
		NewMySQLRestoreRunnerWithCommandRunner(
			commandRunner,
		)

	result, err :=
		runner.Run(
			context.Background(),
			MySQLRestoreRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				BackupFile:            backupFile,
				TemporaryDatabaseName: "restore_verify_orders_100",
				Cleanup:               false,
			},
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.Status != "RESTORED" {
		t.Fatalf("expected RESTORED, got %s", result.Status)
	}

	if len(commandRunner.commands) != 2 {
		t.Fatalf("expected 2 commands, got %d", len(commandRunner.commands))
	}
}

func TestMySQLRestoreRunnerReturnsFailedWhenRestoreCommandFails(t *testing.T) {
	backupFile :=
		createTestBackupFile(t)

	commandRunner :=
		&fakeCommandRunner{
			failOnCommand: 2,
			failWithError: fmt.Errorf(
				"restore failed",
			),
		}

	runner :=
		NewMySQLRestoreRunnerWithCommandRunner(
			commandRunner,
		)

	result, err :=
		runner.Run(
			context.Background(),
			MySQLRestoreRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				BackupFile:            backupFile,
				TemporaryDatabaseName: "restore_verify_orders_100",
				Cleanup:               true,
			},
		)

	if err == nil {
		t.Fatalf("expected error")
	}

	if result.Status != "FAILED" {
		t.Fatalf("expected FAILED, got %s", result.Status)
	}

	if result.ErrorCode != "RESTORE_FAILED" {
		t.Fatalf("expected RESTORE_FAILED, got %s", result.ErrorCode)
	}

	if len(commandRunner.commands) != 3 {
		t.Fatalf("expected create, restore, cleanup commands, got %d", len(commandRunner.commands))
	}

	if !containsText(commandRunner.commands[2].args, "DROP DATABASE") {
		t.Fatalf("expected cleanup after restore failure, args=%v", commandRunner.commands[2].args)
	}
}

func TestMySQLRestoreRunnerReturnsCleanupFailedWhenDropDatabaseFails(t *testing.T) {
	backupFile :=
		createTestBackupFile(t)

	commandRunner :=
		&fakeCommandRunner{
			failOnCommand: 3,
			failWithError: fmt.Errorf(
				"drop failed",
			),
		}

	runner :=
		NewMySQLRestoreRunnerWithCommandRunner(
			commandRunner,
		)

	result, err :=
		runner.Run(
			context.Background(),
			MySQLRestoreRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				BackupFile:            backupFile,
				TemporaryDatabaseName: "restore_verify_orders_100",
				Cleanup:               true,
			},
		)

	if err == nil {
		t.Fatalf("expected error")
	}

	if result.Status != "CLEANUP_FAILED" {
		t.Fatalf("expected CLEANUP_FAILED, got %s", result.Status)
	}

	if result.ErrorCode != "CLEANUP_FAILED" {
		t.Fatalf("expected CLEANUP_FAILED, got %s", result.ErrorCode)
	}
}

func TestMySQLRestoreRunnerRejectsUnsafeTemporaryDatabaseName(t *testing.T) {
	backupFile :=
		createTestBackupFile(t)

	runner :=
		NewMySQLRestoreRunnerWithCommandRunner(
			&fakeCommandRunner{},
		)

	_, err :=
		runner.Run(
			context.Background(),
			MySQLRestoreRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				BackupFile:            backupFile,
				TemporaryDatabaseName: "restore-verify-orders;drop",
				Cleanup:               true,
			},
		)

	if err == nil {
		t.Fatalf("expected error")
	}
}

func TestSanitizeDatabaseName(t *testing.T) {
	result :=
		SanitizeDatabaseName(
			"orders-prod/2026",
		)

	if result != "orders_prod_2026" {
		t.Fatalf("unexpected sanitized database name: %s", result)
	}
}

func createTestBackupFile(
	t *testing.T,
) string {
	t.Helper()

	tempDirectory :=
		t.TempDir()

	backupFile :=
		filepath.Join(
			tempDirectory,
			"orders.sql",
		)

	content :=
		"-- MySQL dump\n" +
			"CREATE TABLE `orders` (`id` bigint);\n" +
			"INSERT INTO `orders` VALUES (1);\n"

	if err := os.WriteFile(
		backupFile,
		[]byte(content),
		0600,
	); err != nil {
		t.Fatalf("failed to write backup file: %v", err)
	}

	return backupFile
}

func containsArg(
	args []string,
	expected string,
) bool {
	for _, arg := range args {
		if arg == expected {
			return true
		}
	}

	return false
}

func containsText(
	args []string,
	expectedText string,
) bool {
	for _, arg := range args {
		if strings.Contains(arg, expectedText) {
			return true
		}
	}

	return false
}