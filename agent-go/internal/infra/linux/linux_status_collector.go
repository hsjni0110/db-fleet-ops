package linux

import (
	"context"
	"runtime"
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
	var memoryStats runtime.MemStats

	runtime.ReadMemStats(&memoryStats)

	memoryUsagePercent := 0.0

	if memoryStats.Sys > 0 {
		memoryUsagePercent =
			float64(memoryStats.Alloc) / float64(memoryStats.Sys) * 100.0
	}

	return LinuxStatus{
		CPUUsagePercent:    0.0,
		MemoryUsagePercent: memoryUsagePercent,
		DiskUsagePercent:   0.0,
	}, nil
}