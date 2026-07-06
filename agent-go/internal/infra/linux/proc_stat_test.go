package linux

import "testing"

func TestParseCPUStat(t *testing.T) {
	stat, err := ParseCPUStat(
		"cpu  100 20 30 850 10 0 5 0 0 0",
	)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if stat.User != 100 {
		t.Fatalf("expected user 100, got %d", stat.User)
	}

	if stat.Idle != 850 {
		t.Fatalf("expected idle 850, got %d", stat.Idle)
	}
}

func TestCalculateCPUUsagePercent(t *testing.T) {
	before := CPUStat{
		User:   100,
		System: 100,
		Idle:   800,
	}

	after := CPUStat{
		User:   150,
		System: 150,
		Idle:   900,
	}

	usage := CalculateCPUUsagePercent(
		before,
		after,
	)

	if usage != 50.0 {
		t.Fatalf("expected cpu usage 50.0, got %.2f", usage)
	}
}