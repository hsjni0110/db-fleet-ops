package config

import (
	"os"
	"strconv"
	"time"
)

type Config struct {
	ControlPlaneURL          string
	AgentID                  string
	AgentToken               string
	AgentName                string
	AgentVersion             string
	AgentStateFile           string
	BackupDirectory          string
	HeartbeatIntervalSeconds int
	PollIntervalSeconds      int
}

func Load() Config {
	return Config{
		ControlPlaneURL:          getEnv("CONTROL_PLANE_URL", "http://localhost:8080"),
		AgentID:                  getEnv("AGENT_ID", ""),
		AgentToken:               getEnv("AGENT_TOKEN", ""),
		AgentName:                getEnv("AGENT_NAME", "local-agent"),
		AgentVersion:             getEnv("AGENT_VERSION", "0.1.0"),
		AgentStateFile:           getEnv("AGENT_STATE_FILE", "./agent-state.json"),
		BackupDirectory:          getEnv("BACKUP_DIRECTORY", "/tmp/db-fleetops-backups"),
		HeartbeatIntervalSeconds: getEnvInt("HEARTBEAT_INTERVAL_SECONDS", 10),
		PollIntervalSeconds:      getEnvInt("POLL_INTERVAL_SECONDS", 5),
	}
}

func (c Config) HeartbeatInterval() time.Duration {
	return time.Duration(c.HeartbeatIntervalSeconds) * time.Second
}

func (c Config) PollInterval() time.Duration {
	return time.Duration(c.PollIntervalSeconds) * time.Second
}

func getEnv(key string, defaultValue string) string {
	value := os.Getenv(key)

	if value == "" {
		return defaultValue
	}

	return value
}

func getEnvInt(key string, defaultValue int) int {
	value := os.Getenv(key)

	if value == "" {
		return defaultValue
	}

	parsed, err := strconv.Atoi(value)

	if err != nil {
		return defaultValue
	}

	return parsed
}