package main

import (
	"context"
	"log"

	agenthttp "db-fleetops-agent/internal/infra/http"

	"db-fleetops-agent/internal/application"
	"db-fleetops-agent/internal/config"
	"db-fleetops-agent/internal/domain"
)

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

	controlPlaneClient :=
		agenthttp.NewControlPlaneClient(
			cfg.ControlPlaneURL,
		)

	service := application.NewAgentService(
		controlPlaneClient,
		controlPlaneClient,
		&staticLinuxInfoPort{
			agentInfo: agentInfo,
		},
	)

	ctx := context.Background()

	if err := service.Register(ctx); err != nil {
		log.Fatalf("failed to register agent: %v", err)
	}

	if err := service.SendHeartbeat(ctx); err != nil {
		log.Fatalf("failed to send heartbeat: %v", err)
	}
}