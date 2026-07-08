package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"

	"db-fleetops-agent/internal/application"
	"db-fleetops-agent/internal/config"
	agenthttp "db-fleetops-agent/internal/infra/http"
	agentlinux "db-fleetops-agent/internal/infra/linux"
	agentstate "db-fleetops-agent/internal/infra/state"
	"db-fleetops-agent/internal/task"
)

func main() {
	ctx, stop :=
		signal.NotifyContext(
			context.Background(),
			os.Interrupt,
			syscall.SIGTERM,
		)

	defer stop()

	cfg := config.Load()

	controlPlaneClient :=
		agenthttp.NewControlPlaneClient(
			cfg.ControlPlaneURL,
		)

	stateStore :=
		agentstate.NewFileAgentStateStore(
			cfg.AgentStateFile,
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
					cfg.BackupDirectory,
				),
				task.NewMySQLRestoreVerifyHandler(),
			},
		)

	service :=
		application.NewAgentService(
			controlPlaneClient,
			controlPlaneClient,
			controlPlaneClient,
			linuxInfoCollector,
			dispatcher,
			stateStore,
			controlPlaneClient,
		)

	if err := service.RegisterIfNeeded(ctx); err != nil {
		log.Fatalf(
			"failed to prepare agent identity: %v",
			err,
		)
	}

	log.Print("agent_runtime_started")

	if err := service.Run(
		ctx,
		cfg.HeartbeatInterval(),
		cfg.PollInterval(),
	); err != nil {
		log.Fatalf(
			"agent runtime failed: %v",
			err,
		)
	}

	log.Print("agent_runtime_exited")
}