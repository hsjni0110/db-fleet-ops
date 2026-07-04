package application

import (
	"context"
	"log"

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
}

func NewAgentService(
	registrationPort port.RegistrationPort,
	heartbeatPort port.HeartbeatPort,
	taskPort port.TaskPort,
	linuxInfoPort port.LinuxInfoPort,
	taskDispatcher TaskDispatcher,
) *AgentService {
	return &AgentService{
		registrationPort: registrationPort,
		heartbeatPort:   heartbeatPort,
		taskPort:        taskPort,
		linuxInfoPort:   linuxInfoPort,
		taskDispatcher:  taskDispatcher,
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