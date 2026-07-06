package linux

import "testing"

func TestParseMemoryInfo(t *testing.T) {
	content := `
MemTotal:        1000000 kB
MemFree:          200000 kB
MemAvailable:     400000 kB
Buffers:           10000 kB
`

	info, err := ParseMemoryInfo(content)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if info.MemTotalKB != 1000000 {
		t.Fatalf(
			"expected MemTotal 1000000, got %d",
			info.MemTotalKB,
		)
	}

	if info.MemAvailableKB != 400000 {
		t.Fatalf(
			"expected MemAvailable 400000, got %d",
			info.MemAvailableKB,
		)
	}

	usage := info.UsagePercent()

	if usage != 60.0 {
		t.Fatalf("expected memory usage 60.0, got %.2f", usage)
	}
}