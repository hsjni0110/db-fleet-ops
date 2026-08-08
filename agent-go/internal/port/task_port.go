package port

import (
	"context"
	"errors"
)

var ErrTaskExecutionConflict = errors.New("task execution conflict")

type Task struct {
	TaskID           int64
	TaskType         string
	ParametersJSON   string
	ExecutionAttempt int
	CredentialID     int64
}

type TaskCredential struct {
	Username string
	Password string
}

type TaskPort interface {
	FetchNextTask(
		ctx context.Context,
	) (*Task, error)

	RenewTaskLease(
		ctx context.Context,
		taskID int64,
		executionAttempt int,
	) error

	ResolveTaskCredential(ctx context.Context, taskID int64,
		executionAttempt int) (TaskCredential, error)

	CompleteTask(
		ctx context.Context,
		taskID int64,
		executionAttempt int,
		resultReportID string,
		resultPayloadJSON string,
	) error

	FailTask(
		ctx context.Context,
		taskID int64,
		executionAttempt int,
		resultReportID string,
		errorCode string,
		errorMessage string,
	) error
}
