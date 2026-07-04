package main

import (
	"context"
	"log"

	"db-fleetops-agent/internal/application"
	"db-fleetops-agent/internal/config"
	"db-fleetops-agent/internal/domain"
)

type noopHeartbeatPort struct {
}

func (n *noopHeartbeatPort) SendHeartbeat(
	ctx context.Context,
	agentInfo domain.AgentInfo,
) error {
	log.Printf(
		"noop_heartbeat agentName=%s hostname=%s os=%s version=%s",
		agentInfo.AgentName,
		agentInfo.Hostname,
		agentInfo.OSName,
		agentInfo.AgentVersion,
	)

	return nil
}

type staticLinuxInfoPort struct {
	agentInfo domain.AgentInfo
}

func (s *staticLinuxInfoPort) CollectAgentInfo(
	ctx context.Context,
) (domain.AgentInfo, error) {
	return s.agentInfo, nil
}

func main() {
	cfg := config.Load()

	agentInfo := application.NewStaticAgentInfo(
		cfg.AgentName,
		"localhost",
		"127.0.0.1",
		"Linux",
		"amd64",
		cfg.AgentVersion,
	)

	service := application.NewAgentService(
		&noopHeartbeatPort{},
		&staticLinuxInfoPort{
			agentInfo: agentInfo,
		},
	)

	if err := service.SendHeartbeat(context.Background()); err != nil {
		log.Fatalf("failed to send heartbeat: %v", err)
	}
}