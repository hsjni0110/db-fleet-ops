package linux

import (
	"context"
	"os"
	"runtime"
	"strings"
	"time"
)

type LinuxStatus struct {
	CPUUsagePercent    float64 `json:"cpuUsagePercent"`
	MemoryUsagePercent float64 `json:"memoryUsagePercent"`
	DiskUsagePercent   float64 `json:"diskUsagePercent"`
}

type LinuxStatusCollector struct {
}

func NewLinuxStatusCollector() *LinuxStatusCollector {
	return &LinuxStatusCollector{}
}

func (c *LinuxStatusCollector) Collect(
	ctx context.Context,
) (LinuxStatus, error) {
	if runtime.GOOS != "linux" {
		return LinuxStatus{
			CPUUsagePercent:    0.0,
			MemoryUsagePercent: 0.0,
			DiskUsagePercent:   0.0,
		}, nil
	}

	cpuUsagePercent, err := collectCPUUsagePercent(ctx)

	if err != nil {
		return LinuxStatus{}, err
	}

	memoryUsagePercent, err := collectMemoryUsagePercent()

	if err != nil {
		return LinuxStatus{}, err
	}

	diskUsagePercent, err := DiskUsagePercent("/")

	if err != nil {
		return LinuxStatus{}, err
	}

	return LinuxStatus{
		CPUUsagePercent:    cpuUsagePercent,
		MemoryUsagePercent: memoryUsagePercent,
		DiskUsagePercent:   diskUsagePercent,
	}, nil
}

func collectCPUUsagePercent(
	ctx context.Context,
) (float64, error) {
	before, err := readCPUStat()

	if err != nil {
		return 0.0, err
	}

	select {
	case <-ctx.Done():
		return 0.0, ctx.Err()

	case <-time.After(200 * time.Millisecond):
	}

	after, err := readCPUStat()

	if err != nil {
		return 0.0, err
	}

	return CalculateCPUUsagePercent(
		before,
		after,
	), nil
}

func readCPUStat() (CPUStat, error) {
	fileBytes, err := os.ReadFile("/proc/stat")

	if err != nil {
		return CPUStat{}, err
	}

	lines := strings.Split(string(fileBytes), "\n")

	return ParseCPUStat(lines[0])
}

func collectMemoryUsagePercent() (float64, error) {
	fileBytes, err := os.ReadFile("/proc/meminfo")

	if err != nil {
		return 0.0, err
	}

	info, err := ParseMemoryInfo(
		string(fileBytes),
	)

	if err != nil {
		return 0.0, err
	}

	return info.UsagePercent(), nil
}