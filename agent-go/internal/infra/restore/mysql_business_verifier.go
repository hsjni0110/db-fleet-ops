package restore

import (
	"context"
	"database/sql"
	"fmt"
	"regexp"
	"strings"
	"time"
)

type BusinessRule struct {
	Code          string `json:"code"`
	Name          string `json:"name"`
	Query         string `json:"query"`
	TimeoutMillis int64  `json:"timeoutMillis"`
}

type MySQLBusinessVerifyRequest struct {
	Host                  string
	Port                  int
	Username              string
	Password              string
	TemporaryDatabaseName string
	ProfileKey            string
	Rules                 []BusinessRule
}

type BusinessRuleResult struct {
	Code           string `json:"code"`
	Name           string `json:"name"`
	Status         string `json:"status"`
	ViolationCount *int64 `json:"violationCount,omitempty"`
	DurationMillis int64  `json:"durationMillis"`
	Message        string `json:"message,omitempty"`
}

type MySQLBusinessVerifyResult struct {
	ProfileKey       string               `json:"profileKey,omitempty"`
	Status           string               `json:"status"`
	CheckedRuleCount int                  `json:"checkedRuleCount"`
	FailedRuleCount  int                  `json:"failedRuleCount"`
	RuleResults      []BusinessRuleResult `json:"ruleResults"`
}

type businessTransaction interface {
	QueryContext(context.Context, string, ...any) (*sql.Rows, error)
	Rollback() error
}

type businessDatabase interface {
	BeginTx(context.Context, *sql.TxOptions) (businessTransaction, error)
	Close() error
}

type sqlBusinessDatabase struct{ database *sql.DB }
type sqlBusinessTransaction struct{ transaction *sql.Tx }

func (d sqlBusinessDatabase) BeginTx(ctx context.Context, options *sql.TxOptions) (businessTransaction, error) {
	transaction, err := d.database.BeginTx(ctx, options)
	if err != nil {
		return nil, err
	}
	return sqlBusinessTransaction{transaction: transaction}, nil
}

func (d sqlBusinessDatabase) Close() error { return d.database.Close() }

func (t sqlBusinessTransaction) QueryContext(ctx context.Context, query string, args ...any) (*sql.Rows, error) {
	return t.transaction.QueryContext(ctx, query, args...)
}

func (t sqlBusinessTransaction) Rollback() error { return t.transaction.Rollback() }

type MySQLBusinessVerifier struct {
	openDatabase func(string, string) (businessDatabase, error)
}

func NewMySQLBusinessVerifier() *MySQLBusinessVerifier {
	return &MySQLBusinessVerifier{openDatabase: func(driverName, dataSourceName string) (businessDatabase, error) {
		database, err := sql.Open(driverName, dataSourceName)
		if err != nil {
			return nil, err
		}
		return sqlBusinessDatabase{database: database}, nil
	}}
}

func NewMySQLBusinessVerifierWithOpenDatabase(openDatabase func(string, string) (businessDatabase, error)) *MySQLBusinessVerifier {
	return &MySQLBusinessVerifier{openDatabase: openDatabase}
}

func (v *MySQLBusinessVerifier) Verify(ctx context.Context, request MySQLBusinessVerifyRequest) MySQLBusinessVerifyResult {
	if request.ProfileKey == "" {
		return MySQLBusinessVerifyResult{Status: "NOT_APPLIED", RuleResults: []BusinessRuleResult{}}
	}

	result := MySQLBusinessVerifyResult{ProfileKey: request.ProfileKey, Status: "VERIFIED", RuleResults: make([]BusinessRuleResult, 0, len(request.Rules))}
	database, err := v.openDatabase("mysql", buildBusinessDataSourceName(request))
	if err != nil {
		return businessSetupError(request, err)
	}
	defer database.Close()

	for _, rule := range request.Rules {
		ruleResult := v.verifyRule(ctx, database, rule)
		result.RuleResults = append(result.RuleResults, ruleResult)
		result.CheckedRuleCount++
		if ruleResult.Status != "VERIFIED" {
			result.FailedRuleCount++
		}
		if ruleResult.Status == "ERROR" {
			result.Status = "ERROR"
		} else if ruleResult.Status == "FAILED" && result.Status == "VERIFIED" {
			result.Status = "FAILED"
		}
	}
	return result
}

func (v *MySQLBusinessVerifier) verifyRule(ctx context.Context, database businessDatabase, rule BusinessRule) BusinessRuleResult {
	startedAt := time.Now()
	result := BusinessRuleResult{Code: rule.Code, Name: rule.Name}
	finishError := func(err error) BusinessRuleResult {
		result.Status = "ERROR"
		result.Message = err.Error()
		result.DurationMillis = time.Since(startedAt).Milliseconds()
		return result
	}

	if err := validateBusinessRule(rule); err != nil {
		return finishError(err)
	}
	timeout := time.Duration(rule.TimeoutMillis) * time.Millisecond
	ruleContext, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	transaction, err := database.BeginTx(ruleContext, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return finishError(err)
	}
	defer transaction.Rollback()

	rows, err := transaction.QueryContext(ruleContext, strings.TrimSpace(rule.Query))
	if err != nil {
		return finishError(err)
	}
	defer rows.Close()
	columns, err := rows.Columns()
	if err != nil || len(columns) != 1 || !strings.EqualFold(columns[0], "violation_count") {
		return finishError(fmt.Errorf("Business Rule must return exactly one violation_count column"))
	}
	if !rows.Next() {
		if rows.Err() != nil {
			return finishError(rows.Err())
		}
		return finishError(fmt.Errorf("Business Rule must return exactly one row"))
	}
	var violationCount int64
	if err := rows.Scan(&violationCount); err != nil {
		return finishError(err)
	}
	if violationCount < 0 || rows.Next() {
		return finishError(fmt.Errorf("violation_count must be a single non-negative value"))
	}
	if err := rows.Err(); err != nil {
		return finishError(err)
	}
	result.ViolationCount = &violationCount
	result.DurationMillis = time.Since(startedAt).Milliseconds()
	if violationCount == 0 {
		result.Status = "VERIFIED"
		result.Message = "Business Rule verified"
	} else {
		result.Status = "FAILED"
		result.Message = fmt.Sprintf("Business Rule found %d violation(s)", violationCount)
	}
	return result
}

var forbiddenBusinessKeyword = regexp.MustCompile(`(?i)\b(INSERT|UPDATE|DELETE|REPLACE|MERGE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|CALL|LOAD|OUTFILE|DUMPFILE|INTO)\b`)

func validateBusinessRule(rule BusinessRule) error {
	query := strings.TrimSpace(rule.Query)
	if rule.Code == "" || rule.Name == "" || rule.TimeoutMillis <= 0 {
		return fmt.Errorf("Business Rule code, name and positive timeoutMillis are required")
	}
	if !regexp.MustCompile(`(?i)^SELECT\b`).MatchString(query) {
		return fmt.Errorf("Business Rule query must start with SELECT")
	}
	if strings.Contains(query, ";") || strings.Contains(query, "--") || strings.Contains(query, "#") || strings.Contains(query, "/*") || strings.Contains(query, "*/") {
		return fmt.Errorf("Business Rule query contains forbidden delimiter or comment")
	}
	if forbiddenBusinessKeyword.MatchString(query) {
		return fmt.Errorf("Business Rule query contains forbidden keyword")
	}
	return nil
}

func buildBusinessDataSourceName(request MySQLBusinessVerifyRequest) string {
	return fmt.Sprintf("%s:%s@tcp(%s:%d)/%s?parseTime=true", request.Username, request.Password, request.Host, request.Port, request.TemporaryDatabaseName)
}

func businessSetupError(request MySQLBusinessVerifyRequest, err error) MySQLBusinessVerifyResult {
	results := make([]BusinessRuleResult, 0, len(request.Rules))
	for _, rule := range request.Rules {
		results = append(results, BusinessRuleResult{Code: rule.Code, Name: rule.Name, Status: "ERROR", Message: err.Error()})
	}
	return MySQLBusinessVerifyResult{ProfileKey: request.ProfileKey, Status: "ERROR", CheckedRuleCount: len(results), FailedRuleCount: len(results), RuleResults: results}
}
