package linux

import (
	"fmt"
	"strconv"
	"strings"
)

type MemoryInfo struct {
	MemTotalKB     uint64
	MemAvailableKB uint64
}

func ParseMemoryInfo(content string) (MemoryInfo, error) {
	var info MemoryInfo

	lines := strings.Split(content, "\n")

	for _, line := range lines {
		fields := strings.Fields(line)

		if len(fields) < 2 {
			continue
		}

		key := strings.TrimSuffix(fields[0], ":")

		value, err := strconv.ParseUint(fields[1], 10, 64)

		if err != nil {
			continue
		}

		switch key {
		case "MemTotal":
			info.MemTotalKB = value
		case "MemAvailable":
			info.MemAvailableKB = value
		}
	}

	if info.MemTotalKB == 0 {
		return MemoryInfo{}, fmt.Errorf("MemTotal not found")
	}

	return info, nil
}

func (m MemoryInfo) UsagePercent() float64 {
	if m.MemTotalKB == 0 {
		return 0.0
	}

	used := m.MemTotalKB - m.MemAvailableKB

	return float64(used) /
		float64(m.MemTotalKB) *
		100.0
}