package application

import (
	"context"
	"errors"
	"testing"
	"time"

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
	fetchCalled      bool
	renewCalled      bool
	renewCount       int
	renewError       error
	completeCalled   bool
	failCalled       bool
	task             *port.Task
	resultPayload    string
	resultReportID   string
	credentialCalled bool
	credentialError  error
}

func (f *fakeTaskPort) FetchNextTask(
	ctx context.Context,
) (*port.Task, error) {
	f.fetchCalled = true
	return f.task, nil
}

func (f *fakeTaskPort) RenewTaskLease(
	ctx context.Context,
	taskID int64,
	executionAttempt int,
) error {
	f.renewCalled = true
	f.renewCount++
	return f.renewError
}

func (f *fakeTaskPort) ResolveTaskCredential(ctx context.Context, taskID int64,
	executionAttempt int) (port.TaskCredential, error) {
	f.credentialCalled = true
	if f.credentialError != nil {
		return port.TaskCredential{}, f.credentialError
	}
	return port.TaskCredential{Username: "root", Password: "secret"}, nil
}

func (f *fakeTaskPort) CompleteTask(
	ctx context.Context,
	taskID int64,
	executionAttempt int,
	resultReportID string,
	resultPayloadJSON string,
) error {
	f.completeCalled = true
	f.resultPayload = resultPayloadJSON
	f.resultReportID = resultReportID
	return nil
}

func (f *fakeTaskPort) FailTask(
	ctx context.Context,
	taskID int64,
	executionAttempt int,
	resultReportID string,
	errorCode string,
	errorMessage string,
) error {
	f.failCalled = true
	f.resultReportID = resultReportID
	return nil
}

type fakeDispatcher struct {
	resultPayload       string
	waitForCancellation bool
	called              bool
	task                port.Task
}

func (f *fakeDispatcher) Dispatch(
	ctx context.Context,
	task port.Task,
) (string, error) {
	f.called = true
	f.task = task
	if f.waitForCancellation {
		<-ctx.Done()
		return "", ctx.Err()
	}
	return f.resultPayload, nil
}

func TestPollAndHandleTaskResolvesCredentialJustBeforeDispatch(t *testing.T) {
	taskPort := &fakeTaskPort{task: &port.Task{TaskID: 10, TaskType: "MYSQL_LOGICAL_BACKUP",
		ParametersJSON: `{"host":"mysql"}`, ExecutionAttempt: 1, CredentialID: 7}}
	dispatcher := &fakeDispatcher{resultPayload: `{}`}
	service := NewAgentService(&fakeRegistrationPort{}, &fakeHeartbeatPort{}, taskPort,
		&fakeLinuxInfoPort{}, dispatcher, &fakeStateStorePort{}, &fakeIdentityPort{})

	if err := service.PollAndHandleTask(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !taskPort.credentialCalled || !dispatcher.called {
		t.Fatal("expected credential lookup before task dispatch")
	}
	if dispatcher.task.ParametersJSON != `{"host":"mysql","password":"secret","username":"root"}` {
		t.Fatalf("credential was not added to in-memory task: %s", dispatcher.task.ParametersJSON)
	}
}

func TestPollAndHandleTaskStopsOnCredentialConflict(t *testing.T) {
	taskPort := &fakeTaskPort{task: &port.Task{TaskID: 10, TaskType: "MYSQL_LOGICAL_BACKUP",
		ParametersJSON: `{}`, ExecutionAttempt: 1, CredentialID: 7},
		credentialError: port.ErrTaskExecutionConflict}
	dispatcher := &fakeDispatcher{}
	service := NewAgentService(&fakeRegistrationPort{}, &fakeHeartbeatPort{}, taskPort,
		&fakeLinuxInfoPort{}, dispatcher, &fakeStateStorePort{}, &fakeIdentityPort{})

	if err := service.PollAndHandleTask(context.Background()); err != nil {
		t.Fatalf("credential conflict should end stale execution quietly: %v", err)
	}
	if dispatcher.called || taskPort.completeCalled || taskPort.failCalled {
		t.Fatal("stale execution must not run or report a result")
	}
}

func TestPollAndHandleTaskRenewsLeaseDuringLongTask(t *testing.T) {
	taskPort := &fakeTaskPort{task: &port.Task{
		TaskID: 10, TaskType: "COLLECT_LINUX_STATUS", ParametersJSON: "{}", ExecutionAttempt: 1,
	}}
	service := NewAgentService(&fakeRegistrationPort{}, &fakeHeartbeatPort{}, taskPort,
		&fakeLinuxInfoPort{}, &fakeDispatcher{waitForCancellation: true},
		&fakeStateStorePort{}, &fakeIdentityPort{})
	service.leaseRenewalInterval = 5 * time.Millisecond
	service.taskLeaseDuration = 30 * time.Millisecond

	done := make(chan error, 1)
	go func() { done <- service.PollAndHandleTask(context.Background()) }()
	time.Sleep(18 * time.Millisecond)
	if taskPort.renewCount < 2 {
		t.Fatalf("expected multiple lease renewals, got %d", taskPort.renewCount)
	}
	// A conflict proves the server no longer owns this execution and cancels the handler.
	taskPort.renewError = port.ErrTaskExecutionConflict
	select {
	case err := <-done:
		if err != nil && !errors.Is(err, context.Canceled) {
			t.Fatalf("unexpected error: %v", err)
		}
	case <-time.After(time.Second):
		t.Fatal("expected stale task execution to be cancelled")
	}
	if taskPort.completeCalled || taskPort.failCalled {
		t.Fatal("stale execution must not report completion or failure")
	}
}

type fakeStateStorePort struct {
	state domain.AgentState
	saved bool
}

func (f *fakeStateStorePort) Load(
	ctx context.Context,
) (domain.AgentState, error) {
	return f.state, nil
}

func (f *fakeStateStorePort) Save(
	ctx context.Context,
	state domain.AgentState,
) error {
	f.saved = true
	f.state = state

	return nil
}

type fakeIdentityPort struct {
	called     bool
	agentID    int64
	agentToken string
}

func (f *fakeIdentityPort) SetAgentIdentity(
	agentID int64,
	agentToken string,
) {
	f.called = true
	f.agentID = agentID
	f.agentToken = agentToken
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
	taskPort := &fakeTaskPort{}
	linuxInfoPort := &fakeLinuxInfoPort{
		agentInfo: expectedInfo,
	}
	dispatcher := &fakeDispatcher{}
	stateStore := &fakeStateStorePort{}
	identityPort := &fakeIdentityPort{}

	service := NewAgentService(
		registrationPort,
		heartbeatPort,
		taskPort,
		linuxInfoPort,
		dispatcher,
		stateStore,
		identityPort,
	)

	err := service.Register(context.Background())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !registrationPort.called {
		t.Fatal("expected registration port to be called")
	}
}

func TestRegisterIfNeededReusesExistingState(t *testing.T) {
	registrationPort := &fakeRegistrationPort{}
	heartbeatPort := &fakeHeartbeatPort{}
	taskPort := &fakeTaskPort{}
	linuxInfoPort := &fakeLinuxInfoPort{}
	dispatcher := &fakeDispatcher{}
	stateStore := &fakeStateStorePort{
		state: domain.AgentState{
			AgentID:    10,
			AgentToken: "agent-token-existing",
		},
	}
	identityPort := &fakeIdentityPort{}

	service := NewAgentService(
		registrationPort,
		heartbeatPort,
		taskPort,
		linuxInfoPort,
		dispatcher,
		stateStore,
		identityPort,
	)

	err := service.RegisterIfNeeded(context.Background())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if registrationPort.called {
		t.Fatal("expected registration port not to be called")
	}

	if !identityPort.called {
		t.Fatal("expected identity port to be called")
	}

	if identityPort.agentID != 10 {
		t.Fatalf("expected agentID 10, got %d", identityPort.agentID)
	}

	if identityPort.agentToken != "agent-token-existing" {
		t.Fatalf(
			"expected existing token, got %s",
			identityPort.agentToken,
		)
	}
}

func TestRegisterIfNeededRegistersAndSavesStateWhenStateIsEmpty(t *testing.T) {
	registrationPort := &fakeRegistrationPort{}
	heartbeatPort := &fakeHeartbeatPort{}
	taskPort := &fakeTaskPort{}
	linuxInfoPort := &fakeLinuxInfoPort{
		agentInfo: domain.AgentInfo{
			AgentName:    "local-agent",
			Hostname:     "localhost",
			IPAddress:    "127.0.0.1",
			OSName:       "Linux",
			Architecture: "amd64",
			AgentVersion: "0.1.0",
		},
	}
	dispatcher := &fakeDispatcher{}
	stateStore := &fakeStateStorePort{}
	identityPort := &fakeIdentityPort{}

	service := NewAgentService(
		registrationPort,
		heartbeatPort,
		taskPort,
		linuxInfoPort,
		dispatcher,
		stateStore,
		identityPort,
	)

	err := service.RegisterIfNeeded(context.Background())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !registrationPort.called {
		t.Fatal("expected registration port to be called")
	}

	if !stateStore.saved {
		t.Fatal("expected state store to save state")
	}

	if stateStore.state.AgentID != 1 {
		t.Fatalf(
			"expected saved agentID 1, got %d",
			stateStore.state.AgentID,
		)
	}

	if stateStore.state.AgentToken != "agent-token-001" {
		t.Fatalf(
			"expected saved token agent-token-001, got %s",
			stateStore.state.AgentToken,
		)
	}

	if !identityPort.called {
		t.Fatal("expected identity port to be called")
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
	taskPort := &fakeTaskPort{}
	linuxInfoPort := &fakeLinuxInfoPort{
		agentInfo: expectedInfo,
	}
	dispatcher := &fakeDispatcher{}
	stateStore := &fakeStateStorePort{}
	identityPort := &fakeIdentityPort{}

	service := NewAgentService(
		registrationPort,
		heartbeatPort,
		taskPort,
		linuxInfoPort,
		dispatcher,
		stateStore,
		identityPort,
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
			TaskID:           10,
			TaskType:         "COLLECT_LINUX_STATUS",
			ParametersJSON:   "{}",
			ExecutionAttempt: 1,
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
		&fakeStateStorePort{},
		&fakeIdentityPort{},
	)

	err := service.PollAndHandleTask(context.Background())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !taskPort.fetchCalled {
		t.Fatal("expected FetchNextTask to be called")
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
	if taskPort.resultReportID == "" {
		t.Fatal("expected result report ID to be generated")
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
		&fakeStateStorePort{},
		&fakeIdentityPort{},
	)

	err := service.PollAndHandleTask(context.Background())

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !taskPort.fetchCalled {
		t.Fatal("expected FetchNextTask to be called")
	}

}

func TestRunStopsWhenContextIsCancelled(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())

	taskPort := &fakeTaskPort{
		task: nil,
	}

	service := NewAgentService(
		&fakeRegistrationPort{},
		&fakeHeartbeatPort{},
		taskPort,
		&fakeLinuxInfoPort{
			agentInfo: domain.AgentInfo{
				AgentName:    "local-agent",
				Hostname:     "localhost",
				IPAddress:    "127.0.0.1",
				OSName:       "Linux",
				Architecture: "amd64",
				AgentVersion: "0.1.0",
			},
		},
		&fakeDispatcher{},
		&fakeStateStorePort{},
		&fakeIdentityPort{},
	)

	done := make(chan error, 1)

	go func() {
		done <- service.Run(
			ctx,
			10*time.Millisecond,
			10*time.Millisecond,
			10*time.Millisecond,
			30*time.Millisecond,
		)
	}()

	time.Sleep(30 * time.Millisecond)
	cancel()

	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("expected no error, got %v", err)
		}

	case <-time.After(1 * time.Second):
		t.Fatal("expected runtime loop to stop")
	}
}
