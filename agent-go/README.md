# DB FleetOps Go Agent

DB FleetOps Agent는 관리 대상 DB Host에서 실행되는 경량 Agent입니다.

현재 구현 범위는 Go Agent의 기본 구조입니다.

## 현재 구현

- Config 로딩
- AgentInfo 도메인
- HeartbeatPort 인터페이스
- TaskPort 인터페이스
- LinuxInfoPort 인터페이스
- AgentService 기본 흐름
- Noop Heartbeat 실행

## 아직 미구현

- Control Plane HTTP Client
- Agent 등록 API 호출
- Heartbeat API 호출
- Task Polling
- Linux 상태 수집
- MySQL 백업 Handler

## 실행

```bash
cd agent-go
go test ./...
go run ./cmd/db-fleet-agent