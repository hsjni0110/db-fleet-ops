package port

import (
	"context"

	"db-fleetops-agent/internal/domain"
)

type HeartbeatPort interface {
	SendHeartbeat(
		ctx context.Context,
		agentInfo domain.AgentInfo,
	) error
}