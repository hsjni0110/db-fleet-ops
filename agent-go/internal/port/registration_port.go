package port

import (
	"context"

	"db-fleetops-agent/internal/domain"
)

type RegistrationResult struct {
	AgentID    int64
	AgentToken string
	Status     string
}

type RegistrationPort interface {
	RegisterAgent(
		ctx context.Context,
		agentInfo domain.AgentInfo,
	) (RegistrationResult, error)
}