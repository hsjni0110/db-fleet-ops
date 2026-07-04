package port

import (
	"context"

	"db-fleetops-agent/internal/domain"
)

type LinuxInfoPort interface {
	CollectAgentInfo(
		ctx context.Context,
	) (domain.AgentInfo, error)
}