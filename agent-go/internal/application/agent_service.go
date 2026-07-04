package application

import (
	"context"
	"log"

	"db-fleetops-agent/internal/domain"
	"db-fleetops-agent/internal/port"
)

type AgentService struct {
	registrationPort port.RegistrationPort
	heartbeatPort   port.HeartbeatPort
	linuxInfoPort   port.LinuxInfoPort
}

func NewAgentService(
	registrationPort port.RegistrationPort,
	heartbeatPort port.HeartbeatPort,
	linuxInfoPort port.LinuxInfoPort,
) *AgentService {
	return &AgentService{
		registrationPort: registrationPort,
		heartbeatPort:   heartbeatPort,
		linuxInfoPort:   linuxInfoPort,
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