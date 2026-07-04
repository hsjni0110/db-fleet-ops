package linux

import (
	"context"
	"net"
	"os"
	"runtime"

	"db-fleetops-agent/internal/domain"
)

type LinuxInfoCollector struct {
	agentName    string
	agentVersion string
}

func NewLinuxInfoCollector(
	agentName string,
	agentVersion string,
) *LinuxInfoCollector {
	return &LinuxInfoCollector{
		agentName:    agentName,
		agentVersion: agentVersion,
	}
}

func (c *LinuxInfoCollector) CollectAgentInfo(
	ctx context.Context,
) (domain.AgentInfo, error) {
	hostname, err := os.Hostname()

	if err != nil {
		return domain.AgentInfo{}, err
	}

	return domain.AgentInfo{
		AgentName:    c.agentName,
		Hostname:     hostname,
		IPAddress:    firstNonLoopbackIP(),
		OSName:       runtime.GOOS,
		Architecture: runtime.GOARCH,
		AgentVersion: c.agentVersion,
	}, nil
}

func firstNonLoopbackIP() string {
	interfaces, err := net.Interfaces()

	if err != nil {
		return "unknown"
	}

	for _, networkInterface := range interfaces {
		addresses, err := networkInterface.Addrs()

		if err != nil {
			continue
		}

		for _, address := range addresses {
			ipNet, ok := address.(*net.IPNet)

			if !ok {
				continue
			}

			ip := ipNet.IP

			if ip == nil || ip.IsLoopback() {
				continue
			}

			ipv4 := ip.To4()

			if ipv4 == nil {
				continue
			}

			return ipv4.String()
		}
	}

	return "unknown"
}