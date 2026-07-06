package port

type AgentIdentityPort interface {
	SetAgentIdentity(
		agentID int64,
		agentToken string,
	)
}