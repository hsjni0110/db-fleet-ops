package restore

import (
	"context"
	"database/sql"
	"fmt"
	"sort"
	"strings"

	_ "github.com/go-sql-driver/mysql"
)

type MySQLRestoreVerifyRequest struct {
	Host                  string
	Port                  int
	Username              string
	Password              string
	TemporaryDatabaseName string
	ExpectedTables        []string
	VerifyRowCount        bool
}

type MySQLRestoreVerifyResult struct {
	Status                string                           `json:"status"`
	TemporaryDatabaseName string                           `json:"temporaryDatabaseName"`
	RestoredTableCount    int                              `json:"restoredTableCount"`
	CheckedTableCount     int                              `json:"checkedTableCount"`
	TotalRowCount         int64                            `json:"totalRowCount"`
	Items                 []MySQLRestoreVerifyItemResult   `json:"items"`
	Message               string                           `json:"message"`
	ErrorCode             string                           `json:"errorCode,omitempty"`
	ErrorMessage          string                           `json:"errorMessage,omitempty"`
}

type MySQLRestoreVerifyItemResult struct {
	TableName          string `json:"tableName"`
	ExistsInRestoredDB bool   `json:"existsInRestoredDb"`
	RowCount           *int64 `json:"rowCount,omitempty"`
	Status             string `json:"status"`
	Message            string `json:"message"`
}

type SQLDatabase interface {
	QueryContext(
		ctx context.Context,
		query string,
		args ...any,
	) (*sql.Rows, error)

	QueryRowContext(
		ctx context.Context,
		query string,
		args ...any,
	) *sql.Row

	Close() error
}

type MySQLRestoreVerifier struct {
	openDatabase func(
		driverName string,
		dataSourceName string,
	) (SQLDatabase, error)
}

func NewMySQLRestoreVerifier() *MySQLRestoreVerifier {
	return &MySQLRestoreVerifier{
		openDatabase: func(
			driverName string,
			dataSourceName string,
		) (SQLDatabase, error) {
			return sql.Open(
				driverName,
				dataSourceName,
			)
		},
	}
}

func NewMySQLRestoreVerifierWithOpenDatabase(
	openDatabase func(
		driverName string,
		dataSourceName string,
	) (SQLDatabase, error),
) *MySQLRestoreVerifier {
	return &MySQLRestoreVerifier{
		openDatabase: openDatabase,
	}
}

func (v *MySQLRestoreVerifier) Verify(
	ctx context.Context,
	request MySQLRestoreVerifyRequest,
) (MySQLRestoreVerifyResult, error) {
	if err := validateMySQLRestoreVerifyRequest(request); err != nil {
		return MySQLRestoreVerifyResult{}, err
	}

	dataSourceName :=
		buildMySQLDataSourceName(request)

	database, err :=
		v.openDatabase(
			"mysql",
			dataSourceName,
		)

	if err != nil {
		return MySQLRestoreVerifyResult{}, err
	}

	defer database.Close()

	tables, err :=
		v.loadTables(
			ctx,
			database,
		)

	if err != nil {
		return MySQLRestoreVerifyResult{
				Status:                "FAILED",
				TemporaryDatabaseName: request.TemporaryDatabaseName,
				ErrorCode:             "TABLE_LIST_FAILED",
				ErrorMessage:          err.Error(),
			},
			err
	}

	if len(tables) == 0 {
		return MySQLRestoreVerifyResult{
				Status:                "FAILED",
				TemporaryDatabaseName: request.TemporaryDatabaseName,
				RestoredTableCount:    0,
				CheckedTableCount:     0,
				TotalRowCount:         0,
				Items:                 []MySQLRestoreVerifyItemResult{},
				ErrorCode:             "NO_TABLE_RESTORED",
				ErrorMessage:          "restored database has no tables",
			},
			fmt.Errorf("restored database has no tables")
	}

	tableSet :=
		toTableSet(tables)

	targetTables :=
		selectTargetTables(
			tables,
			request.ExpectedTables,
		)

	items :=
		make(
			[]MySQLRestoreVerifyItemResult,
			0,
			len(targetTables),
		)

	var totalRowCount int64 = 0
	checkedTableCount := 0
	hasFailure := false

	for _, tableName := range targetTables {
		if !tableSet[strings.ToLower(tableName)] {
			hasFailure = true

			items =
				append(
					items,
					MySQLRestoreVerifyItemResult{
						TableName:          tableName,
						ExistsInRestoredDB: false,
						RowCount:           nil,
						Status:             "MISSING",
						Message:            "expected table is missing in restored database",
					},
				)

			continue
		}

		if !request.VerifyRowCount {
			checkedTableCount++

			items =
				append(
					items,
					MySQLRestoreVerifyItemResult{
						TableName:          tableName,
						ExistsInRestoredDB: true,
						RowCount:           nil,
						Status:             "SKIPPED",
						Message:            "row count verification skipped",
					},
				)

			continue
		}

		rowCount, err :=
			v.countRows(
				ctx,
				database,
				tableName,
			)

		if err != nil {
			hasFailure = true

			items =
				append(
					items,
					MySQLRestoreVerifyItemResult{
						TableName:          tableName,
						ExistsInRestoredDB: true,
						RowCount:           nil,
						Status:             "COUNT_FAILED",
						Message:            err.Error(),
					},
				)

			continue
		}

		checkedTableCount++
		totalRowCount += rowCount

		rowCountValue :=
			rowCount

		items =
			append(
				items,
				MySQLRestoreVerifyItemResult{
					TableName:          tableName,
					ExistsInRestoredDB: true,
					RowCount:           &rowCountValue,
					Status:             "VERIFIED",
					Message:            "table verified",
				},
			)
	}

	if hasFailure {
		return MySQLRestoreVerifyResult{
				Status:                "FAILED",
				TemporaryDatabaseName: request.TemporaryDatabaseName,
				RestoredTableCount:    len(tables),
				CheckedTableCount:     checkedTableCount,
				TotalRowCount:         totalRowCount,
				Items:                 items,
				ErrorCode:             "RESTORE_VERIFY_FAILED",
				ErrorMessage:          "one or more restored table checks failed",
			},
			fmt.Errorf("one or more restored table checks failed")
	}

	return MySQLRestoreVerifyResult{
		Status:                "VERIFIED",
		TemporaryDatabaseName: request.TemporaryDatabaseName,
		RestoredTableCount:    len(tables),
		CheckedTableCount:     checkedTableCount,
		TotalRowCount:         totalRowCount,
		Items:                 items,
		Message:               "restore verification completed",
	}, nil
}

func (v *MySQLRestoreVerifier) loadTables(
	ctx context.Context,
	database SQLDatabase,
) ([]string, error) {
	rows, err :=
		database.QueryContext(
			ctx,
			"SHOW TABLES",
		)

	if err != nil {
		return nil, err
	}

	defer rows.Close()

	var tables []string

	for rows.Next() {
		var tableName string

		if err := rows.Scan(&tableName); err != nil {
			return nil, err
		}

		tables =
			append(
				tables,
				tableName,
			)
	}

	if err := rows.Err(); err != nil {
		return nil, err
	}

	sort.Strings(tables)

	return tables, nil
}

func (v *MySQLRestoreVerifier) countRows(
	ctx context.Context,
	database SQLDatabase,
	tableName string,
) (int64, error) {
	if !isSafeTableName(tableName) {
		return 0, fmt.Errorf(
			"tableName contains unsafe characters: %s",
			tableName,
		)
	}

	query :=
		fmt.Sprintf(
			"SELECT COUNT(*) FROM `%s`",
			tableName,
		)

	var count int64

	if err :=
		database.QueryRowContext(
			ctx,
			query,
		).Scan(&count); err != nil {
		return 0, err
	}

	return count, nil
}

func validateMySQLRestoreVerifyRequest(
	request MySQLRestoreVerifyRequest,
) error {
	if request.Host == "" {
		return fmt.Errorf("host is required")
	}

	if request.Port <= 0 {
		return fmt.Errorf("port is required")
	}

	if request.Username == "" {
		return fmt.Errorf("username is required")
	}

	if request.TemporaryDatabaseName == "" {
		return fmt.Errorf("temporaryDatabaseName is required")
	}

	if !isSafeDatabaseName(request.TemporaryDatabaseName) {
		return fmt.Errorf(
			"temporaryDatabaseName contains unsafe characters: %s",
			request.TemporaryDatabaseName,
		)
	}

	for _, tableName := range request.ExpectedTables {
		if tableName == "" {
			return fmt.Errorf("expected table name must not be blank")
		}

		if !isSafeTableName(tableName) {
			return fmt.Errorf(
				"expected table name contains unsafe characters: %s",
				tableName,
			)
		}
	}

	return nil
}

func buildMySQLDataSourceName(
	request MySQLRestoreVerifyRequest,
) string {
	return fmt.Sprintf(
		"%s:%s@tcp(%s:%d)/%s?parseTime=true",
		request.Username,
		request.Password,
		request.Host,
		request.Port,
		request.TemporaryDatabaseName,
	)
}

func selectTargetTables(
	restoredTables []string,
	expectedTables []string,
) []string {
	if len(expectedTables) > 0 {
		result :=
			append(
				[]string{},
				expectedTables...,
			)

		sort.Strings(result)

		return result
	}

	result :=
		append(
			[]string{},
			restoredTables...,
		)

	sort.Strings(result)

	return result
}

func toTableSet(
	tables []string,
) map[string]bool {
	result :=
		make(
			map[string]bool,
			len(tables),
		)

	for _, tableName := range tables {
		result[strings.ToLower(tableName)] = true
	}

	return result
}

func isSafeTableName(
	value string,
) bool {
	if value == "" {
		return false
	}

	for _, r := range value {
		if r >= 'a' && r <= 'z' {
			continue
		}

		if r >= 'A' && r <= 'Z' {
			continue
		}

		if r >= '0' && r <= '9' {
			continue
		}

		if r == '_' {
			continue
		}

		return false
	}

	return true
}