package domain

type AgentState struct {
	AgentID    int64  `json:"agentId"`
	AgentToken string `json:"agentToken"`
}

func (s AgentState) IsEmpty() bool {
	return s.AgentID == 0 || s.AgentToken == ""
}