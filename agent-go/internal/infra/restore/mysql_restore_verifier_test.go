package restore

import (
	"context"
	"database/sql"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
)

func TestMySQLRestoreVerifierVerifiesExpectedTablesWithRowCount(t *testing.T) {
	database, mock, err :=
		sqlmock.New()

	if err != nil {
		t.Fatalf("failed to create sqlmock: %v", err)
	}

	defer database.Close()

	verifier :=
		NewMySQLRestoreVerifierWithOpenDatabase(
			func(
				driverName string,
				dataSourceName string,
			) (SQLDatabase, error) {
				return database, nil
			},
		)

	mock.ExpectQuery("SHOW TABLES").
		WillReturnRows(
			sqlmock.NewRows(
				[]string{
					"Tables_in_restore_verify_orders_100",
				},
			).
				AddRow("orders").
				AddRow("order_items"),
		)

	mock.ExpectQuery("SELECT COUNT\\(\\*\\) FROM `order_items`").
		WillReturnRows(
			sqlmock.NewRows(
				[]string{
					"count",
				},
			).AddRow(26512),
		)

	mock.ExpectQuery("SELECT COUNT\\(\\*\\) FROM `orders`").
		WillReturnRows(
			sqlmock.NewRows(
				[]string{
					"count",
				},
			).AddRow(12000),
		)

	result, err :=
		verifier.Verify(
			context.Background(),
			MySQLRestoreVerifyRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				TemporaryDatabaseName: "restore_verify_orders_100",
				ExpectedTables: []string{
					"orders",
					"order_items",
				},
				VerifyRowCount: true,
			},
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.Status != "VERIFIED" {
		t.Fatalf("expected VERIFIED, got %s", result.Status)
	}

	if result.RestoredTableCount != 2 {
		t.Fatalf("expected restored table count 2, got %d", result.RestoredTableCount)
	}

	if result.CheckedTableCount != 2 {
		t.Fatalf("expected checked table count 2, got %d", result.CheckedTableCount)
	}

	if result.TotalRowCount != 38512 {
		t.Fatalf("expected total row count 38512, got %d", result.TotalRowCount)
	}

	if len(result.Items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(result.Items))
	}

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestMySQLRestoreVerifierFailsWhenExpectedTableIsMissing(t *testing.T) {
	database, mock, err :=
		sqlmock.New()

	if err != nil {
		t.Fatalf("failed to create sqlmock: %v", err)
	}

	defer database.Close()

	verifier :=
		NewMySQLRestoreVerifierWithOpenDatabase(
			func(
				driverName string,
				dataSourceName string,
			) (SQLDatabase, error) {
				return database, nil
			},
		)

	mock.ExpectQuery("SHOW TABLES").
		WillReturnRows(
			sqlmock.NewRows(
				[]string{
					"Tables_in_restore_verify_orders_100",
				},
			).
				AddRow("orders"),
		)

	result, err :=
		verifier.Verify(
			context.Background(),
			MySQLRestoreVerifyRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				TemporaryDatabaseName: "restore_verify_orders_100",
				ExpectedTables: []string{
					"orders",
					"order_items",
				},
				VerifyRowCount: true,
			},
		)

	if err == nil {
		t.Fatalf("expected error")
	}

	if result.Status != "FAILED" {
		t.Fatalf("expected FAILED, got %s", result.Status)
	}

	if result.ErrorCode != "RESTORE_VERIFY_FAILED" {
		t.Fatalf("expected RESTORE_VERIFY_FAILED, got %s", result.ErrorCode)
	}

	if len(result.Items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(result.Items))
	}

	var missingFound bool

	for _, item := range result.Items {
		if item.TableName == "order_items" && item.Status == "MISSING" {
			missingFound = true
		}
	}

	if !missingFound {
		t.Fatalf("expected missing item for order_items")
	}

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestMySQLRestoreVerifierSkipsRowCountWhenDisabled(t *testing.T) {
	database, mock, err :=
		sqlmock.New()

	if err != nil {
		t.Fatalf("failed to create sqlmock: %v", err)
	}

	defer database.Close()

	verifier :=
		NewMySQLRestoreVerifierWithOpenDatabase(
			func(
				driverName string,
				dataSourceName string,
			) (SQLDatabase, error) {
				return database, nil
			},
		)

	mock.ExpectQuery("SHOW TABLES").
		WillReturnRows(
			sqlmock.NewRows(
				[]string{
					"Tables_in_restore_verify_orders_100",
				},
			).
				AddRow("orders").
				AddRow("order_items"),
		)

	result, err :=
		verifier.Verify(
			context.Background(),
			MySQLRestoreVerifyRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				TemporaryDatabaseName: "restore_verify_orders_100",
				ExpectedTables: []string{
					"orders",
					"order_items",
				},
				VerifyRowCount: false,
			},
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.Status != "VERIFIED" {
		t.Fatalf("expected VERIFIED, got %s", result.Status)
	}

	if result.CheckedTableCount != 2 {
		t.Fatalf("expected checked table count 2, got %d", result.CheckedTableCount)
	}

	if result.TotalRowCount != 0 {
		t.Fatalf("expected total row count 0, got %d", result.TotalRowCount)
	}

	for _, item := range result.Items {
		if item.Status != "SKIPPED" {
			t.Fatalf("expected SKIPPED item, got %s", item.Status)
		}

		if item.RowCount != nil {
			t.Fatalf("expected nil row count when row count verification is skipped")
		}
	}

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestMySQLRestoreVerifierUsesAllRestoredTablesWhenExpectedTablesEmpty(t *testing.T) {
	database, mock, err :=
		sqlmock.New()

	if err != nil {
		t.Fatalf("failed to create sqlmock: %v", err)
	}

	defer database.Close()

	verifier :=
		NewMySQLRestoreVerifierWithOpenDatabase(
			func(
				driverName string,
				dataSourceName string,
			) (SQLDatabase, error) {
				return database, nil
			},
		)

	mock.ExpectQuery("SHOW TABLES").
		WillReturnRows(
			sqlmock.NewRows(
				[]string{
					"Tables_in_restore_verify_orders_100",
				},
			).
				AddRow("orders").
				AddRow("order_items"),
		)

	mock.ExpectQuery("SELECT COUNT\\(\\*\\) FROM `order_items`").
		WillReturnRows(
			sqlmock.NewRows(
				[]string{
					"count",
				},
			).AddRow(26512),
		)

	mock.ExpectQuery("SELECT COUNT\\(\\*\\) FROM `orders`").
		WillReturnRows(
			sqlmock.NewRows(
				[]string{
					"count",
				},
			).AddRow(12000),
		)

	result, err :=
		verifier.Verify(
			context.Background(),
			MySQLRestoreVerifyRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				TemporaryDatabaseName: "restore_verify_orders_100",
				ExpectedTables:        nil,
				VerifyRowCount:        true,
			},
		)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.Status != "VERIFIED" {
		t.Fatalf("expected VERIFIED, got %s", result.Status)
	}

	if result.RestoredTableCount != 2 {
		t.Fatalf("expected restored table count 2, got %d", result.RestoredTableCount)
	}

	if result.CheckedTableCount != 2 {
		t.Fatalf("expected checked table count 2, got %d", result.CheckedTableCount)
	}

	if result.TotalRowCount != 38512 {
		t.Fatalf("expected total row count 38512, got %d", result.TotalRowCount)
	}

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestMySQLRestoreVerifierFailsWhenNoTablesRestored(t *testing.T) {
	database, mock, err :=
		sqlmock.New()

	if err != nil {
		t.Fatalf("failed to create sqlmock: %v", err)
	}

	defer database.Close()

	verifier :=
		NewMySQLRestoreVerifierWithOpenDatabase(
			func(
				driverName string,
				dataSourceName string,
			) (SQLDatabase, error) {
				return database, nil
			},
		)

	mock.ExpectQuery("SHOW TABLES").
		WillReturnRows(
			sqlmock.NewRows(
				[]string{
					"Tables_in_restore_verify_orders_100",
				},
			),
		)

	result, err :=
		verifier.Verify(
			context.Background(),
			MySQLRestoreVerifyRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				TemporaryDatabaseName: "restore_verify_orders_100",
				ExpectedTables:        nil,
				VerifyRowCount:        true,
			},
		)

	if err == nil {
		t.Fatalf("expected error")
	}

	if result.Status != "FAILED" {
		t.Fatalf("expected FAILED, got %s", result.Status)
	}

	if result.ErrorCode != "NO_TABLE_RESTORED" {
		t.Fatalf("expected NO_TABLE_RESTORED, got %s", result.ErrorCode)
	}

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestMySQLRestoreVerifierRejectsUnsafeTableName(t *testing.T) {
	verifier :=
		NewMySQLRestoreVerifierWithOpenDatabase(
			func(
				driverName string,
				dataSourceName string,
			) (SQLDatabase, error) {
				return &sql.DB{}, nil
			},
		)

	_, err :=
		verifier.Verify(
			context.Background(),
			MySQLRestoreVerifyRequest{
				Host:                  "127.0.0.1",
				Port:                  3306,
				Username:              "restore_user",
				Password:              "secret",
				TemporaryDatabaseName: "restore_verify_orders_100",
				ExpectedTables: []string{
					"orders;drop",
				},
				VerifyRowCount: true,
			},
		)

	if err == nil {
		t.Fatalf("expected error")
	}
}