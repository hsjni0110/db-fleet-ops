package main

import (
	"context"
	"log"

	"db-fleetops-agent/internal/application"
	"db-fleetops-agent/internal/config"
	agenthttp "db-fleetops-agent/internal/infra/http"
	agentlinux "db-fleetops-agent/internal/infra/linux"
	agentstate "db-fleetops-agent/internal/infra/state"
	"db-fleetops-agent/internal/task"
)

func main() {
	cfg := config.Load()

	controlPlaneClient :=
		agenthttp.NewControlPlaneClient(
			cfg.ControlPlaneURL,
		)

	stateStore :=
		agentstate.NewFileAgentStateStore(
			"./agent-state.json",
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
		stateStore,
		controlPlaneClient,
	)

	ctx := context.Background()

	if err := service.RegisterIfNeeded(ctx); err != nil {
		log.Fatalf("failed to prepare agent identity: %v", err)
	}

	if err := service.SendHeartbeat(ctx); err != nil {
		log.Fatalf("failed to send heartbeat: %v", err)
	}

	if err := service.PollAndHandleTask(ctx); err != nil {
		log.Fatalf("failed to handle task: %v", err)
	}
}