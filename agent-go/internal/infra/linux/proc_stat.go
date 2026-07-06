package linux

import (
	"fmt"
	"strconv"
	"strings"
)

type CPUStat struct {
	User    uint64
	Nice    uint64
	System  uint64
	Idle    uint64
	IOWait  uint64
	IRQ     uint64
	SoftIRQ uint64
	Steal   uint64
}

func ParseCPUStat(line string) (CPUStat, error) {
	fields := strings.Fields(line)

	if len(fields) < 8 || fields[0] != "cpu" {
		return CPUStat{}, fmt.Errorf("invalid cpu stat line")
	}

	values := make([]uint64, 0, len(fields)-1)

	for _, field := range fields[1:] {
		value, err := strconv.ParseUint(field, 10, 64)

		if err != nil {
			return CPUStat{}, err
		}

		values = append(values, value)
	}

	return CPUStat{
		User:    values[0],
		Nice:    values[1],
		System:  values[2],
		Idle:    values[3],
		IOWait:  values[4],
		IRQ:     values[5],
		SoftIRQ: values[6],
		Steal:   values[7],
	}, nil
}

func (s CPUStat) IdleTime() uint64 {
	return s.Idle + s.IOWait
}

func (s CPUStat) TotalTime() uint64 {
	return s.User +
		s.Nice +
		s.System +
		s.Idle +
		s.IOWait +
		s.IRQ +
		s.SoftIRQ +
		s.Steal
}

func CalculateCPUUsagePercent(
	before CPUStat,
	after CPUStat,
) float64 {
	totalDelta := after.TotalTime() - before.TotalTime()
	idleDelta := after.IdleTime() - before.IdleTime()

	if totalDelta == 0 {
		return 0.0
	}

	return float64(totalDelta-idleDelta) /
		float64(totalDelta) *
		100.0
}