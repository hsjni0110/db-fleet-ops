package port

import "context"

type Task struct {
	TaskID         int64
	TaskType       string
	ParametersJSON string
}

type TaskPort interface {
	FetchNextTask(
		ctx context.Context,
	) (*Task, error)

	StartTask(
		ctx context.Context,
		taskID int64,
	) error

	CompleteTask(
		ctx context.Context,
		taskID int64,
		resultPayloadJSON string,
	) error

	FailTask(
		ctx context.Context,
		taskID int64,
		errorCode string,
		errorMessage string,
	) error
}