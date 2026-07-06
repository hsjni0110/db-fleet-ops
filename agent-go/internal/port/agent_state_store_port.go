package port

import (
	"context"

	"db-fleetops-agent/internal/domain"
)

type AgentStateStorePort interface {
	Load(
		ctx context.Context,
	) (domain.AgentState, error)

	Save(
		ctx context.Context,
		state domain.AgentState,
	) error
}