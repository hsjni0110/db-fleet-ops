package application

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"time"

	"db-fleetops-agent/internal/domain"
	"db-fleetops-agent/internal/port"
)

type TaskDispatcher interface {
	Dispatch(
		ctx context.Context,
		task port.Task,
	) (string, error)
}

type AgentService struct {
	registrationPort     port.RegistrationPort
	heartbeatPort        port.HeartbeatPort
	taskPort             port.TaskPort
	linuxInfoPort        port.LinuxInfoPort
	taskDispatcher       TaskDispatcher
	stateStorePort       port.AgentStateStorePort
	identityPort         port.AgentIdentityPort
	leaseRenewalInterval time.Duration
	taskLeaseDuration    time.Duration
}

func NewAgentService(
	registrationPort port.RegistrationPort,
	heartbeatPort port.HeartbeatPort,
	taskPort port.TaskPort,
	linuxInfoPort port.LinuxInfoPort,
	taskDispatcher TaskDispatcher,
	stateStorePort port.AgentStateStorePort,
	identityPort port.AgentIdentityPort,
) *AgentService {
	return &AgentService{
		registrationPort:     registrationPort,
		heartbeatPort:        heartbeatPort,
		taskPort:             taskPort,
		linuxInfoPort:        linuxInfoPort,
		taskDispatcher:       taskDispatcher,
		stateStorePort:       stateStorePort,
		identityPort:         identityPort,
		leaseRenewalInterval: 20 * time.Second,
		taskLeaseDuration:    60 * time.Second,
	}
}

func (s *AgentService) Register(
	ctx context.Context,
) error {
	agentInfo, err := s.linuxInfoPort.CollectAgentInfo(ctx)

	if err != nil {
		return err
	}

	result, err := s.registrationPort.RegisterAgent(
		ctx,
		agentInfo,
	)

	if err != nil {
		return err
	}

	log.Printf(
		"agent_registered agentId=%d status=%s",
		result.AgentID,
		result.Status,
	)

	return nil
}

func (s *AgentService) RegisterIfNeeded(
	ctx context.Context,
) error {
	state, err := s.stateStorePort.Load(ctx)

	if err != nil {
		return err
	}

	if !state.IsEmpty() {
		s.identityPort.SetAgentIdentity(
			state.AgentID,
			state.AgentToken,
		)

		log.Printf(
			"agent_identity_loaded agentId=%d",
			state.AgentID,
		)

		return nil
	}

	agentInfo, err := s.linuxInfoPort.CollectAgentInfo(ctx)

	if err != nil {
		return err
	}

	result, err := s.registrationPort.RegisterAgent(
		ctx,
		agentInfo,
	)

	if err != nil {
		return err
	}

	newState := domain.AgentState{
		AgentID:    result.AgentID,
		AgentToken: result.AgentToken,
	}

	if err := s.stateStorePort.Save(
		ctx,
		newState,
	); err != nil {
		return err
	}

	s.identityPort.SetAgentIdentity(
		result.AgentID,
		result.AgentToken,
	)

	log.Printf(
		"agent_registered_and_state_saved agentId=%d status=%s",
		result.AgentID,
		result.Status,
	)

	return nil
}

func (s *AgentService) SendHeartbeat(
	ctx context.Context,
) error {
	agentInfo, err := s.linuxInfoPort.CollectAgentInfo(ctx)

	if err != nil {
		return err
	}

	err = s.heartbeatPort.SendHeartbeat(
		ctx,
		agentInfo,
	)

	if err != nil {
		return err
	}

	log.Printf(
		"heartbeat_sent agentName=%s hostname=%s version=%s",
		agentInfo.AgentName,
		agentInfo.Hostname,
		agentInfo.AgentVersion,
	)

	return nil
}

func (s *AgentService) PollAndHandleTask(
	ctx context.Context,
) error {
	nextTask, err := s.taskPort.FetchNextTask(ctx)

	if err != nil {
		return err
	}

	if nextTask == nil {
		log.Print("agent_task_not_found")
		return nil
	}

	log.Printf(
		"agent_task_received taskId=%d taskType=%s",
		nextTask.TaskID,
		nextTask.TaskType,
	)

	taskContext, cancelTask := context.WithCancel(ctx)
	defer cancelTask()
	leaseDone := make(chan struct{})
	leaseLost := make(chan struct{}, 1)
	go s.maintainTaskLease(taskContext, *nextTask, cancelTask, leaseDone, leaseLost)
	if nextTask.CredentialID != 0 {
		credential, resolveErr := s.taskPort.ResolveTaskCredential(taskContext, nextTask.TaskID,
			nextTask.ExecutionAttempt)
		if resolveErr != nil {
			cancelTask()
			<-leaseDone
			if errors.Is(resolveErr, port.ErrTaskExecutionConflict) {
				return nil
			}
			return resolveErr
		}
		if err := addCredentialToParameters(nextTask, credential); err != nil {
			cancelTask()
			<-leaseDone
			return err
		}
	}
	resultPayload, err := s.taskDispatcher.Dispatch(
		taskContext,
		*nextTask,
	)
	cancelTask()
	<-leaseDone
	select {
	case <-leaseLost:
		log.Printf("agent_task_abandoned taskId=%d executionAttempt=%d",
			nextTask.TaskID, nextTask.ExecutionAttempt)
		return nil
	default:
	}

	if err != nil {
		resultReportID, idErr := newResultReportID()
		if idErr != nil {
			return idErr
		}
		failErr := s.taskPort.FailTask(
			ctx,
			nextTask.TaskID,
			nextTask.ExecutionAttempt,
			resultReportID,
			"TASK_EXECUTION_FAILED",
			err.Error(),
		)

		if failErr != nil {
			return failErr
		}

		return err
	}

	resultReportID, err := newResultReportID()
	if err != nil {
		return err
	}
	if err := s.taskPort.CompleteTask(
		ctx,
		nextTask.TaskID,
		nextTask.ExecutionAttempt,
		resultReportID,
		resultPayload,
	); err != nil {
		return err
	}

	log.Printf(
		"agent_task_completed taskId=%d taskType=%s",
		nextTask.TaskID,
		nextTask.TaskType,
	)

	return nil
}

func addCredentialToParameters(task *port.Task, credential port.TaskCredential) error {
	var parameters map[string]any
	if err := json.Unmarshal([]byte(task.ParametersJSON), &parameters); err != nil {
		return err
	}
	parameters["username"] = credential.Username
	parameters["password"] = credential.Password
	encoded, err := json.Marshal(parameters)
	if err != nil {
		return err
	}
	task.ParametersJSON = string(encoded)
	return nil
}

func (s *AgentService) maintainTaskLease(ctx context.Context, task port.Task,
	cancelTask context.CancelFunc, done chan<- struct{}, leaseLost chan<- struct{}) {
	defer close(done)
	ticker := time.NewTicker(s.leaseRenewalInterval)
	defer ticker.Stop()
	lastSuccess := time.Now()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			err := s.taskPort.RenewTaskLease(ctx, task.TaskID, task.ExecutionAttempt)
			if err == nil {
				lastSuccess = time.Now()
				continue
			}
			if errors.Is(err, port.ErrTaskExecutionConflict) || time.Since(lastSuccess) >= s.taskLeaseDuration {
				leaseLost <- struct{}{}
				cancelTask()
				return
			}
			log.Printf("task_lease_renewal_failed taskId=%d executionAttempt=%d error=%v",
				task.TaskID, task.ExecutionAttempt, err)
		}
	}
}

func NewStaticAgentInfo(
	agentName string,
	hostname string,
	ipAddress string,
	osName string,
	architecture string,
	agentVersion string,
) domain.AgentInfo {
	return domain.AgentInfo{
		AgentName:    agentName,
		Hostname:     hostname,
		IPAddress:    ipAddress,
		OSName:       osName,
		Architecture: architecture,
		AgentVersion: agentVersion,
	}
}

func (s *AgentService) Run(
	ctx context.Context,
	heartbeatInterval time.Duration,
	pollInterval time.Duration,
	leaseRenewalInterval time.Duration,
	taskLeaseDuration time.Duration,
) error {
	s.leaseRenewalInterval = leaseRenewalInterval
	s.taskLeaseDuration = taskLeaseDuration
	heartbeatContext, stopHeartbeat := context.WithCancel(ctx)
	defer stopHeartbeat()
	go s.runHeartbeatLoop(heartbeatContext, heartbeatInterval)
	pollTicker := time.NewTicker(pollInterval)

	defer pollTicker.Stop()

	if err := s.PollAndHandleTask(ctx); err != nil {
		return err
	}

	for {
		select {
		case <-ctx.Done():
			log.Print("agent_runtime_stopped")
			return nil

		case <-pollTicker.C:
			if err := s.PollAndHandleTask(ctx); err != nil {
				log.Printf("task_polling_failed error=%v", err)
			}
		}
	}
}

func (s *AgentService) runHeartbeatLoop(ctx context.Context, interval time.Duration) {
	if err := s.SendHeartbeat(ctx); err != nil {
		log.Printf("heartbeat_failed error=%v", err)
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := s.SendHeartbeat(ctx); err != nil {
				log.Printf("heartbeat_failed error=%v", err)
			}
		}
	}
}
