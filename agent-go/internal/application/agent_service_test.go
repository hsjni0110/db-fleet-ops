package application

import (
	"context"
	"testing"

	"db-fleetops-agent/internal/domain"
)

type fakeHeartbeatPort struct {
	called    bool
	agentInfo domain.AgentInfo
}

func (f *fakeHeartbeatPort) SendHeartbeat(
	ctx context.Context,
	agentInfo domain.AgentInfo,
) error {
	f.called = true
	f.agentInfo = agentInfo

	return nil
}

type fakeLinuxInfoPort struct {
	agentInfo domain.AgentInfo
}

func (f *fakeLinuxInfoPort) CollectAgentInfo(
	ctx context.Context,
) (domain.AgentInfo, error) {
	return f.agentInfo, nil
}

func TestSendHeartbeatCollectsAgentInfoAndSendsHeartbeat(t *testing.T) {
	expectedInfo := domain.AgentInfo{
		AgentName:    "local-agent",
		Hostname:     "localhost",
		IPAddress:    "127.0.0.1",
		OSName:       "Linux",
		Architecture: "amd64",
		AgentVersion: "0.1.0",
	}

	heartbeatPort := &fakeHeartbeatPort{}
	linuxInfoPort := &fakeLinuxInfoPort{
		agentInfo: expectedInfo,
	}

	service := NewAgentService(
		heartbeatPort,
		linuxInfoPort,
	)

	err := service.SendHeartbeat(context.Background())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !heartbeatPort.called {
		t.Fatal("expected heartbeat port to be called")
	}

	if heartbeatPort.agentInfo.AgentName != expectedInfo.AgentName {
		t.Fatalf(
			"expected agentName %s, got %s",
			expectedInfo.AgentName,
			heartbeatPort.agentInfo.AgentName,
		)
	}
}