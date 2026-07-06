package state

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"

	"db-fleetops-agent/internal/domain"
)

type FileAgentStateStore struct {
	filePath string
}

func NewFileAgentStateStore(
	filePath string,
) *FileAgentStateStore {
	return &FileAgentStateStore{
		filePath: filePath,
	}
}

func (s *FileAgentStateStore) Load(
	ctx context.Context,
) (domain.AgentState, error) {
	_, err := os.Stat(s.filePath)

	if errors.Is(err, os.ErrNotExist) {
		return domain.AgentState{}, nil
	}

	if err != nil {
		return domain.AgentState{}, err
	}

	fileBytes, err := os.ReadFile(s.filePath)

	if err != nil {
		return domain.AgentState{}, err
	}

	var state domain.AgentState

	if err := json.Unmarshal(fileBytes, &state); err != nil {
		return domain.AgentState{}, err
	}

	return state, nil
}

func (s *FileAgentStateStore) Save(
	ctx context.Context,
	state domain.AgentState,
) error {
	directory := filepath.Dir(s.filePath)

	if err := os.MkdirAll(
		directory,
		0700,
	); err != nil {
		return err
	}

	fileBytes, err := json.MarshalIndent(
		state,
		"",
		"  ",
	)

	if err != nil {
		return err
	}

	return os.WriteFile(
		s.filePath,
		fileBytes,
		0600,
	)
}