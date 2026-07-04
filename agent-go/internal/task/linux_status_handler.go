package task

import (
	"context"
	"encoding/json"

	"db-fleetops-agent/internal/infra/linux"
	"db-fleetops-agent/internal/port"
)

const TaskTypeCollectLinuxStatus = "COLLECT_LINUX_STATUS"

type LinuxStatusHandler struct {
	collector *linux.LinuxStatusCollector
}

func NewLinuxStatusHandler(
	collector *linux.LinuxStatusCollector,
) *LinuxStatusHandler {
	return &LinuxStatusHandler{
		collector: collector,
	}
}

func (h *LinuxStatusHandler) Supports(
	taskType string,
) bool {
	return taskType == TaskTypeCollectLinuxStatus
}

func (h *LinuxStatusHandler) Handle(
	ctx context.Context,
	task port.Task,
) (string, error) {
	status, err := h.collector.Collect(ctx)

	if err != nil {
		return "", err
	}

	resultBytes, err := json.Marshal(status)

	if err != nil {
		return "", err
	}

	return string(resultBytes), nil
}