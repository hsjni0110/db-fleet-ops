package task

import (
	"context"
	"strings"
	"testing"

	"db-fleetops-agent/internal/infra/linux"
	"db-fleetops-agent/internal/port"
)

func TestLinuxStatusHandlerSupportsCollectLinuxStatus(t *testing.T) {
	handler := NewLinuxStatusHandler(
		linux.NewLinuxStatusCollector(),
	)

	if !handler.Supports("COLLECT_LINUX_STATUS") {
		t.Fatal("expected handler to support COLLECT_LINUX_STATUS")
	}

	if handler.Supports("MYSQL_LOGICAL_BACKUP") {
		t.Fatal("expected handler not to support MYSQL_LOGICAL_BACKUP")
	}
}

func TestLinuxStatusHandlerReturnsJSONPayload(t *testing.T) {
	handler := NewLinuxStatusHandler(
		linux.NewLinuxStatusCollector(),
	)

	result, err := handler.Handle(
		context.Background(),
		port.Task{
			TaskID:         1,
			TaskType:       "COLLECT_LINUX_STATUS",
			ParametersJSON: "{}",
		},
	)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !strings.Contains(result, "cpuUsagePercent") {
		t.Fatalf("expected result to contain cpuUsagePercent, got %s", result)
	}

	if !strings.Contains(result, "memoryUsagePercent") {
		t.Fatalf("expected result to contain memoryUsagePercent, got %s", result)
	}

	if !strings.Contains(result, "diskUsagePercent") {
		t.Fatalf("expected result to contain diskUsagePercent, got %s", result)
	}
}