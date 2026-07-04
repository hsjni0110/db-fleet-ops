package main

import (
	"context"
	"log"

	agenthttp "db-fleetops-agent/internal/infra/http"
	agentlinux "db-fleetops-agent/internal/infra/linux"
	"db-fleetops-agent/internal/task"

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

	linuxStatusCollector :=
		agentlinux.NewLinuxStatusCollector()

	dispatcher :=
		task.NewDispatcher(
			[]task.Handler{
				task.NewLinuxStatusHandler(
					linuxStatusCollector,
				),
				task.NewMySQLBackupHandler(
					"/tmp/db-fleetops-backups",
				),
			},
		)

	service := application.NewAgentService(
		controlPlaneClient,
		controlPlaneClient,
		controlPlaneClient,
		linuxInfoCollector,
		dispatcher,
	)

	ctx := context.Background()

	if err := service.Register(ctx); err != nil {
		log.Fatalf("failed to register agent: %v", err)
	}

	if err := service.SendHeartbeat(ctx); err != nil {
		log.Fatalf("failed to send heartbeat: %v", err)
	}

	if err := service.PollAndHandleTask(ctx); err != nil {
		log.Fatalf("failed to handle task: %v", err)
	}
}