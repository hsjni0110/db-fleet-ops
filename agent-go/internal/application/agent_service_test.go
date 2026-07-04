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

type fakeTaskPort struct {
	fetchCalled    bool
	startCalled    bool
	completeCalled bool
	failCalled     bool
	task           *port.Task
	resultPayload  string
}

func (f *fakeTaskPort) FetchNextTask(
	ctx context.Context,
) (*port.Task, error) {
	f.fetchCalled = true
	return f.task, nil
}

func (f *fakeTaskPort) StartTask(
	ctx context.Context,
	taskID int64,
) error {
	f.startCalled = true
	return nil
}

func (f *fakeTaskPort) CompleteTask(
	ctx context.Context,
	taskID int64,
	resultPayloadJSON string,
) error {
	f.completeCalled = true
	f.resultPayload = resultPayloadJSON
	return nil
}

func (f *fakeTaskPort) FailTask(
	ctx context.Context,
	taskID int64,
	errorCode string,
	errorMessage string,
) error {
	f.failCalled = true
	return nil
}

type fakeDispatcher struct {
	resultPayload string
}

func (f *fakeDispatcher) Dispatch(
	ctx context.Context,
	task port.Task,
) (string, error) {
	return f.resultPayload, nil
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
	taskPort := &fakeTaskPort{}
	dispatcher := &fakeDispatcher{}

	service := NewAgentService(
		registrationPort,
		heartbeatPort,
		taskPort,
		linuxInfoPort,
		dispatcher,
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
	taskPort := &fakeTaskPort{}
	dispatcher := &fakeDispatcher{}

	service := NewAgentService(
		registrationPort,
		heartbeatPort,
		taskPort,
		linuxInfoPort,
		dispatcher,
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

func TestPollAndHandleTaskCompletesTask(t *testing.T) {
	taskPort := &fakeTaskPort{
		task: &port.Task{
			TaskID:         10,
			TaskType:       "COLLECT_LINUX_STATUS",
			ParametersJSON: "{}",
		},
	}

	service := NewAgentService(
		&fakeRegistrationPort{},
		&fakeHeartbeatPort{},
		taskPort,
		&fakeLinuxInfoPort{},
		&fakeDispatcher{
			resultPayload: "{\"cpuUsagePercent\":12.5}",
		},
	)

	err := service.PollAndHandleTask(context.Background())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !taskPort.fetchCalled {
		t.Fatal("expected FetchNextTask to be called")
	}

	if !taskPort.startCalled {
		t.Fatal("expected StartTask to be called")
	}

	if !taskPort.completeCalled {
		t.Fatal("expected CompleteTask to be called")
	}

	if taskPort.resultPayload != "{\"cpuUsagePercent\":12.5}" {
		t.Fatalf(
			"unexpected result payload: %s",
			taskPort.resultPayload,
		)
	}
}

func TestPollAndHandleTaskDoesNothingWhenTaskIsEmpty(t *testing.T) {
	taskPort := &fakeTaskPort{
		task: nil,
	}

	service := NewAgentService(
		&fakeRegistrationPort{},
		&fakeHeartbeatPort{},
		taskPort,
		&fakeLinuxInfoPort{},
		&fakeDispatcher{},
	)

	err := service.PollAndHandleTask(context.Background())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !taskPort.fetchCalled {
		t.Fatal("expected FetchNextTask to be called")
	}

	if taskPort.startCalled {
		t.Fatal("expected StartTask not to be called")
	}
}