package restore

import (
	"context"
	"regexp"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
)

func TestMySQLBusinessVerifierReturnsVerifiedAndFailedRules(t *testing.T) {
	database, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	verifier := NewMySQLBusinessVerifierWithOpenDatabase(func(string, string) (businessDatabase, error) {
		return sqlBusinessDatabase{database: database}, nil
	})
	verifiedQuery := "SELECT COUNT(*) AS violation_count FROM policy WHERE 1 = 0"
	failedQuery := "SELECT COUNT(*) AS violation_count FROM claim WHERE status = 'PAID'"
	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta(verifiedQuery)).WillReturnRows(
		sqlmock.NewRows([]string{"violation_count"}).AddRow(0))
	mock.ExpectRollback()
	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta(failedQuery)).WillReturnRows(
		sqlmock.NewRows([]string{"violation_count"}).AddRow(2))
	mock.ExpectRollback()

	result := verifier.Verify(context.Background(), MySQLBusinessVerifyRequest{
		Host: "127.0.0.1", Port: 3306, Username: "user", Password: "secret",
		TemporaryDatabaseName: "restore_verify_insurance", ProfileKey: "INSURANCE_SANDBOX_V1",
		Rules: []BusinessRule{
			{Code: "RULE_ZERO", Name: "zero", Query: verifiedQuery, TimeoutMillis: 5000},
			{Code: "RULE_TWO", Name: "two", Query: failedQuery, TimeoutMillis: 5000},
		},
	})

	if result.Status != "FAILED" || result.CheckedRuleCount != 2 || result.FailedRuleCount != 1 {
		t.Fatalf("unexpected result: %+v", result)
	}
	if *result.RuleResults[0].ViolationCount != 0 || *result.RuleResults[1].ViolationCount != 2 {
		t.Fatalf("unexpected violation counts: %+v", result.RuleResults)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}

func TestValidateBusinessRuleRejectsUnsafeSQL(t *testing.T) {
	queries := []string{
		"UPDATE policy SET status = 'ACTIVE'",
		"SELECT 1; SELECT 2",
		"SELECT 1 -- comment",
		"SELECT 1 /* comment */",
		"SELECT value INTO @value FROM policy",
		"SELECT 1 FROM policy # comment",
	}
	for _, query := range queries {
		if err := validateBusinessRule(BusinessRule{Code: "RULE", Name: "rule",
			Query: query, TimeoutMillis: 5000}); err == nil {
			t.Fatalf("expected query to be rejected: %s", query)
		}
	}
}

func TestMySQLBusinessVerifierTimesOutRule(t *testing.T) {
	database, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	verifier := NewMySQLBusinessVerifierWithOpenDatabase(func(string, string) (businessDatabase, error) {
		return sqlBusinessDatabase{database: database}, nil
	})
	query := "SELECT COUNT(*) AS violation_count FROM policy"
	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta(query)).WillDelayFor(20 * time.Millisecond).
		WillReturnRows(sqlmock.NewRows([]string{"violation_count"}).AddRow(0))
	mock.ExpectRollback()
	result := verifier.Verify(context.Background(), MySQLBusinessVerifyRequest{
		Host: "127.0.0.1", Port: 3306, Username: "user",
		TemporaryDatabaseName: "restore_verify_insurance", ProfileKey: "INSURANCE_SANDBOX_V1",
		Rules: []BusinessRule{{Code: "TIMEOUT", Name: "timeout", Query: query, TimeoutMillis: 1}},
	})
	if result.Status != "ERROR" || result.RuleResults[0].Status != "ERROR" {
		t.Fatalf("expected timeout ERROR, got %+v", result)
	}
}

func TestMySQLBusinessVerifierRejectsUnsafeQueriesAndContinues(t *testing.T) {
	database, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	verifier := NewMySQLBusinessVerifierWithOpenDatabase(func(string, string) (businessDatabase, error) {
		return sqlBusinessDatabase{database: database}, nil
	})
	safeQuery := "SELECT COUNT(*) AS violation_count FROM policy"
	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta(safeQuery)).WillReturnRows(
		sqlmock.NewRows([]string{"violation_count"}).AddRow(0))
	mock.ExpectRollback()

	result := verifier.Verify(context.Background(), MySQLBusinessVerifyRequest{
		Host: "127.0.0.1", Port: 3306, Username: "user",
		TemporaryDatabaseName: "restore_verify_insurance", ProfileKey: "INSURANCE_SANDBOX_V1",
		Rules: []BusinessRule{
			{Code: "UNSAFE", Name: "unsafe", Query: "SELECT 1; DROP TABLE policy", TimeoutMillis: 5000},
			{Code: "SAFE", Name: "safe", Query: safeQuery, TimeoutMillis: 5000},
		},
	})

	if result.Status != "ERROR" || result.RuleResults[0].Status != "ERROR" ||
		result.RuleResults[1].Status != "VERIFIED" {
		t.Fatalf("expected ERROR and continued execution, got %+v", result)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}

func TestMySQLBusinessVerifierRequiresSingleNonNegativeValue(t *testing.T) {
	database, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	verifier := NewMySQLBusinessVerifierWithOpenDatabase(func(string, string) (businessDatabase, error) {
		return sqlBusinessDatabase{database: database}, nil
	})
	query := "SELECT COUNT(*) AS violation_count FROM policy"
	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta(query)).WillReturnRows(
		sqlmock.NewRows([]string{"violation_count", "extra"}).AddRow(0, 1))
	mock.ExpectRollback()

	result := verifier.Verify(context.Background(), MySQLBusinessVerifyRequest{
		Host: "127.0.0.1", Port: 3306, Username: "user",
		TemporaryDatabaseName: "restore_verify_insurance", ProfileKey: "INSURANCE_SANDBOX_V1",
		Rules: []BusinessRule{{Code: "BAD_SHAPE", Name: "bad shape", Query: query, TimeoutMillis: 5000}},
	})
	if result.Status != "ERROR" {
		t.Fatalf("expected ERROR, got %+v", result)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}
