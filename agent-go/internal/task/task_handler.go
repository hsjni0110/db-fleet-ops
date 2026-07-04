package task

import (
	"context"

	"db-fleetops-agent/internal/port"
)

type Handler interface {
	Supports(
		taskType string,
	) bool

	Handle(
		ctx context.Context,
		task port.Task,
	) (string, error)
}

type Dispatcher struct {
	handlers []Handler
}

func NewDispatcher(
	handlers []Handler,
) *Dispatcher {
	return &Dispatcher{
		handlers: handlers,
	}
}

func (d *Dispatcher) Dispatch(
	ctx context.Context,
	task port.Task,
) (string, error) {
	for _, handler := range d.handlers {
		if handler.Supports(task.TaskType) {
			return handler.Handle(
				ctx,
				task,
			)
		}
	}

	return "", ErrUnsupportedTaskType{
		TaskType: task.TaskType,
	}
}

type ErrUnsupportedTaskType struct {
	TaskType string
}

func (e ErrUnsupportedTaskType) Error() string {
	return "unsupported task type: " + e.TaskType
}