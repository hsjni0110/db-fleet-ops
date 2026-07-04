package application

import (
	"context"
	"log"

	"db-fleetops-agent/internal/domain"
	"db-fleetops-agent/internal/port"
)

type AgentService struct {
	heartbeatPort port.HeartbeatPort
	linuxInfoPort port.LinuxInfoPort
}

func NewAgentService(
	heartbeatPort port.HeartbeatPort,
	linuxInfoPort port.LinuxInfoPort,
) *AgentService {
	return &AgentService{
		heartbeatPort: heartbeatPort,
		linuxInfoPort: linuxInfoPort,
	}
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