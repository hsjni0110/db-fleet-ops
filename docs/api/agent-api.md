# Agent API

## 개요

Agent API는 DB Host에 설치된 Go Agent가 Control Plane과 통신하기 위한 내부 API입니다.

Agent는 외부에서 호출을 기다리지 않고, Control Plane으로 직접 요청을 보냅니다.

```text
Go Agent → Control Plane
```

이 구조를 선택한 이유는 DB 서버에 별도 inbound 포트를 열지 않기 위해서입니다.

---

## 1. Agent 등록

```http
POST /internal/v1/agents/register
```

### Request

```json
{
  "agentName": "local-agent",
  "hostname": "localhost",
  "ipAddress": "127.0.0.1",
  "osName": "linux",
  "agentVersion": "0.1.0"
}
```

### Response

```json
{
  "agentId": 1,
  "agentToken": "agent-token-...",
  "status": "ONLINE"
}
```

---

## 2. Heartbeat

```http
POST /internal/v1/agents/{agentId}/heartbeats
```

### Request

```json
{
  "agentToken": "agent-token-...",
  "cpuUsagePercent": 0.0,
  "memoryUsagePercent": 12.5,
  "diskUsagePercent": 0.0
}
```

### Response

```json
{
  "agentId": 1,
  "status": "ONLINE",
  "lastHeartbeatAt": "2026-07-04T17:30:00"
}
```

---

## 3. Agent Task 생성

```http
POST /internal/v1/agents/tasks
```

### Linux 상태 수집 Task

```json
{
  "agentId": 1,
  "taskType": "COLLECT_LINUX_STATUS",
  "parametersJson": "{}"
}
```

### MySQL 백업 Task

```json
{
  "agentId": 1,
  "taskType": "MYSQL_LOGICAL_BACKUP",
  "parametersJson": "{\"databaseName\":\"orders\",\"backupType\":\"LOGICAL\",\"compression\":true}"
}
```

---

## 4. 다음 Task 조회

```http
GET /internal/v1/agents/{agentId}/tasks/next?agentToken={agentToken}
```

### Task 있음

```json
{
  "hasTask": true,
  "taskId": 1,
  "taskType": "COLLECT_LINUX_STATUS",
  "parametersJson": "{}"
}
```

### Task 없음

```json
{
  "hasTask": false,
  "taskId": null,
  "taskType": null,
  "parametersJson": null
}
```

---

## 5. Task 시작

```http
POST /internal/v1/agents/{agentId}/tasks/{taskId}/start
```

```json
{
  "agentToken": "agent-token-..."
}
```

---

## 6. Task 완료

```http
POST /internal/v1/agents/{agentId}/tasks/{taskId}/complete
```

```json
{
  "agentToken": "agent-token-...",
  "resultPayloadJson": "{\"cpuUsagePercent\":0.0,\"memoryUsagePercent\":12.5,\"diskUsagePercent\":0.0}"
}
```

---

## 7. Task 실패

```http
POST /internal/v1/agents/{agentId}/tasks/{taskId}/fail
```

```json
{
  "agentToken": "agent-token-...",
  "errorCode": "TASK_EXECUTION_FAILED",
  "errorMessage": "unsupported task type"
}
```

---

## 지원 Task Type

| Task Type | 설명 |
|---|---|
| COLLECT_LINUX_STATUS | Linux 상태 수집 |
| MYSQL_LOGICAL_BACKUP | MySQL 논리 백업 Stub |

---

## 현재 한계

아직 다음 기능은 없습니다.

- mTLS
- Agent Token 암호화 저장
- Task Long Polling 대기 처리
- Task 동시 Claim 제어
- 실제 mysqldump 실행
- AgentTask와 OperationJob 자동 연결