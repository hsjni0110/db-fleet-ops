package main

import (
	"context"
	"log"

	agenthttp "db-fleetops-agent/internal/infra/http"
	agentlinux "db-fleetops-agent/internal/infra/linux"

	"db-fleetops-agent/internal/application"
	"db-fleetops-agent/internal/config"
)

func main() {
	cfg := config.Load()

	controlPlaneClient :=
		agenthttp.NewControlPlaneClient(
			cfg.ControlPlaneURL,
		)

	linuxInfoCollector :=
		agentlinux.NewLinuxInfoCollector(
			cfg.AgentName,
			cfg.AgentVersion,
		)

	service := application.NewAgentService(
		controlPlaneClient,
		controlPlaneClient,
		linuxInfoCollector,
	)

	ctx := context.Background()

	if err := service.Register(ctx); err != nil {
		log.Fatalf("failed to register agent: %v", err)
	}

	if err := service.SendHeartbeat(ctx); err != nil {
		log.Fatalf("failed to send heartbeat: %v", err)
	}
}