package state

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"db-fleetops-agent/internal/domain"
)

func TestLoadReturnsEmptyStateWhenFileDoesNotExist(t *testing.T) {
	tempDir := t.TempDir()

	store := NewFileAgentStateStore(
		filepath.Join(
			tempDir,
			"agent-state.json",
		),
	)

	loadedState, err := store.Load(
		context.Background(),
	)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if !loadedState.IsEmpty() {
		t.Fatalf("expected empty state, got %+v", loadedState)
	}
}

func TestSaveAndLoadAgentState(t *testing.T) {
	tempDir := t.TempDir()

	statePath :=
		filepath.Join(
			tempDir,
			"agent-state.json",
		)

	store := NewFileAgentStateStore(
		statePath,
	)

	expectedState := domain.AgentState{
		AgentID:    1,
		AgentToken: "agent-token-001",
	}

	if err := store.Save(
		context.Background(),
		expectedState,
	); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	loadedState, err := store.Load(
		context.Background(),
	)

	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if loadedState.AgentID != expectedState.AgentID {
		t.Fatalf(
			"expected agentId %d, got %d",
			expectedState.AgentID,
			loadedState.AgentID,
		)
	}

	if loadedState.AgentToken != expectedState.AgentToken {
		t.Fatalf(
			"expected agentToken %s, got %s",
			expectedState.AgentToken,
			loadedState.AgentToken,
		)
	}
}

func TestLoadReturnsErrorWhenStateFileIsInvalidJSON(t *testing.T) {
	tempDir := t.TempDir()

	statePath :=
		filepath.Join(
			tempDir,
			"agent-state.json",
		)

	if err := os.WriteFile(
		statePath,
		[]byte("{invalid-json"),
		0600,
	); err != nil {
		t.Fatalf("failed to write invalid state file: %v", err)
	}

	store := NewFileAgentStateStore(
		statePath,
	)

	_, err := store.Load(
		context.Background(),
	)

	if err == nil {
		t.Fatal("expected error, got nil")
	}
}