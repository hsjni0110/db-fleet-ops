package application

import (
	"context"
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
	registrationPort port.RegistrationPort
	heartbeatPort   port.HeartbeatPort
	taskPort        port.TaskPort
	linuxInfoPort   port.LinuxInfoPort
	taskDispatcher  TaskDispatcher
	stateStorePort port.AgentStateStorePort
	identityPort   port.AgentIdentityPort
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
		registrationPort: registrationPort,
		heartbeatPort:   heartbeatPort,
		taskPort:        taskPort,
		linuxInfoPort:   linuxInfoPort,
		taskDispatcher:  taskDispatcher,
		stateStorePort:  stateStorePort,
		identityPort:    identityPort,
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

	if err := s.taskPort.StartTask(ctx, nextTask.TaskID); err != nil {
		return err
	}

	resultPayload, err := s.taskDispatcher.Dispatch(
		ctx,
		*nextTask,
	)

	if err != nil {
		failErr := s.taskPort.FailTask(
			ctx,
			nextTask.TaskID,
			"TASK_EXECUTION_FAILED",
			err.Error(),
		)

		if failErr != nil {
			return failErr
		}

		return err
	}

	if err := s.taskPort.CompleteTask(
		ctx,
		nextTask.TaskID,
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
) error {
	heartbeatTicker := time.NewTicker(heartbeatInterval)
	pollTicker := time.NewTicker(pollInterval)

	defer heartbeatTicker.Stop()
	defer pollTicker.Stop()

	if err := s.SendHeartbeat(ctx); err != nil {
		return err
	}

	if err := s.PollAndHandleTask(ctx); err != nil {
		return err
	}

	for {
		select {
		case <-ctx.Done():
			log.Print("agent_runtime_stopped")
			return nil

		case <-heartbeatTicker.C:
			if err := s.SendHeartbeat(ctx); err != nil {
				log.Printf("heartbeat_failed error=%v", err)
			}

		case <-pollTicker.C:
			if err := s.PollAndHandleTask(ctx); err != nil {
				log.Printf("task_polling_failed error=%v", err)
			}
		}
	}
}