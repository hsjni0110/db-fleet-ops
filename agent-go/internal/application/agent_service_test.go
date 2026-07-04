package application

import (
	"context"
	"testing"

	"db-fleetops-agent/internal/domain"
	"db-fleetops-agent/internal/port"
)

type fakeRegistrationPort struct {
	called bool
}

func (f *fakeRegistrationPort) RegisterAgent(
	ctx context.Context,
	agentInfo domain.AgentInfo,
) (port.RegistrationResult, error) {
	f.called = true

	return port.RegistrationResult{
		AgentID:    1,
		AgentToken: "agent-token-001",
		Status:     "ONLINE",
	}, nil
}

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

func TestRegisterCollectsAgentInfoAndRegistersAgent(t *testing.T) {
	expectedInfo := domain.AgentInfo{
		AgentName:    "local-agent",
		Hostname:     "localhost",
		IPAddress:    "127.0.0.1",
		OSName:       "Linux",
		Architecture: "amd64",
		AgentVersion: "0.1.0",
	}

	registrationPort := &fakeRegistrationPort{}
	heartbeatPort := &fakeHeartbeatPort{}
	linuxInfoPort := &fakeLinuxInfoPort{
		agentInfo: expectedInfo,
	}

	service := NewAgentService(
		registrationPort,
		heartbeatPort,
		linuxInfoPort,
	)

	err := service.Register(context.Background())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !registrationPort.called {
		t.Fatal("expected registration port to be called")
	}
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

	registrationPort := &fakeRegistrationPort{}
	heartbeatPort := &fakeHeartbeatPort{}
	linuxInfoPort := &fakeLinuxInfoPort{
		agentInfo: expectedInfo,
	}

	service := NewAgentService(
		registrationPort,
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