# Agent API

## 개요

Agent API는 DB Host에 설치된 Go Agent가 Control Plane과 통신하기 위한 내부 API입니다.

Agent는 외부에서 호출을 기다리지 않고, Control Plane으로 직접 요청을 보냅니다.

```text
Go Agent → Control Plane
```

이 구조를 선택한 이유는 DB 서버에 별도 inbound 포트를 열지 않기 위해서입니다.

운영 콘솔에서 Agent 상태를 읽기 위한 public query API는 별도로 제공합니다.
internal API는 Agent 런타임이 `agentToken`으로 호출하는 쓰기/폴링 API이고,
console query API는 운영자가 상태를 확인하는 읽기 API입니다.
public query API 응답에는 `agentToken`을 절대 포함하지 않습니다.

현재 Agent는 다음 흐름으로 동작합니다.

```text
Agent 실행
  ↓
Local State 확인
  ↓
Agent 등록 또는 기존 Agent ID/Token 재사용
  ↓
Heartbeat 전송
  ↓
OperationTask Polling
  ↓
Task 실행
  ↓
Complete 또는 Fail 보고
  ↓
반복
```

---

## Console Query API

### Agent 목록 조회

```http
GET /api/v1/agents
```

### Response

```json
[
  {
    "agentId": 1,
    "agentName": "local-agent",
    "hostname": "localhost",
    "ipAddress": "127.0.0.1",
    "osName": "linux",
    "architecture": "amd64",
    "agentVersion": "0.1.0",
    "status": "ONLINE",
    "lastHeartbeatAt": "2026-07-06T17:30:00",
    "heartbeatDelaySeconds": 12,
    "createdAt": "2026-07-06T17:00:00",
    "updatedAt": "2026-07-06T17:30:00"
  }
]
```

### Agent 상세 조회

```http
GET /api/v1/agents/{agentId}
```

### Response

```json
{
  "agent": {
    "agentId": 1,
    "agentName": "local-agent",
    "hostname": "localhost",
    "ipAddress": "127.0.0.1",
    "osName": "linux",
    "architecture": "amd64",
    "agentVersion": "0.1.0",
    "status": "ONLINE",
    "lastHeartbeatAt": "2026-07-06T17:30:00",
    "heartbeatDelaySeconds": 12,
    "createdAt": "2026-07-06T17:00:00",
    "updatedAt": "2026-07-06T17:30:00"
  },
  "recentHostMetrics": [
    {
      "metricId": 10,
      "agentId": 1,
      "cpuUsagePercent": 12.5,
      "memoryUsagePercent": 61.2,
      "diskUsagePercent": 73.8,
      "collectedAt": "2026-07-06T17:30:00"
    }
  ],
  "recentOperationTasks": [
    {
      "taskId": 20,
      "agentId": 1,
      "operationJobId": 30,
      "taskType": "COLLECT_LINUX_STATUS",
      "status": "SUCCEEDED",
      "parametersJson": "{}",
      "resultPayloadJson": "{}",
      "errorCode": null,
      "errorMessage": null,
      "startedAt": "2026-07-06T17:29:00",
      "completedAt": "2026-07-06T17:30:00",
      "createdAt": "2026-07-06T17:28:00"
    }
  ]
}
```

### 설명

Console Query API는 운영 콘솔 화면 전용 읽기 API입니다.

목록 화면에서는 Agent 기본 정보와 Heartbeat 지연 시간을 표시합니다.
상세 화면에서는 최근 Host Metric 10건과 최근 OperationTask 10건을 함께 표시합니다.

`heartbeatDelaySeconds`는 서버 시각 기준으로 `lastHeartbeatAt` 이후 경과 시간을 초 단위로 계산합니다.
Heartbeat가 없는 Agent라면 `heartbeatDelaySeconds`는 `null`입니다.

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
  "architecture": "amd64",
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

### 설명

Agent는 최초 실행 시 Control Plane에 등록합니다.

등록 후 발급받은 `agentId`, `agentToken`은 Go Agent의 Local State 파일에 저장합니다.

이후 재실행 시에는 기존 Local State를 읽어 재등록하지 않고 동일 Agent Identity를 재사용합니다.

```text
최초 실행
  ↓
Register API 호출
  ↓
agent-state.json 저장

재실행
  ↓
agent-state.json 로드
  ↓
기존 agentId / agentToken 재사용
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
  "cpuUsagePercent": 14.2,
  "memoryUsagePercent": 53.7,
  "diskUsagePercent": 61.4
}
```

### Response

```json
{
  "agentId": 1,
  "status": "ONLINE",
  "lastHeartbeatAt": "2026-07-06T17:30:00"
}
```

### 설명

Heartbeat는 Agent가 살아 있는지 확인하기 위한 신호입니다.

현재 Go Agent는 Runtime Loop 안에서 주기적으로 Heartbeat를 전송합니다.

Linux 환경에서는 Host 기준으로 CPU, Memory, Disk 사용률을 수집합니다.

현재 수집 항목은 다음과 같습니다.

| 항목 | 설명 |
|---|---|
| cpuUsagePercent | `/proc/stat` 기반 CPU 사용률 |
| memoryUsagePercent | `/proc/meminfo` 기반 Memory 사용률 |
| diskUsagePercent | `statfs("/")` 기반 Disk 사용률 |

macOS 등 Linux가 아닌 개발 환경에서는 Host Metric 수집값을 `0.0`으로 반환합니다.

---

## 3. OperationTask 생성

```http
POST /internal/v1/agents/tasks
```

### Linux 상태 수집 Task

```json
{
  "agentId": 1,
  "operationJobId": null,
  "taskType": "COLLECT_LINUX_STATUS",
  "parametersJson": "{}"
}
```

### MySQL 백업 Task

```json
{
  "agentId": 1,
  "operationJobId": 100,
  "taskType": "MYSQL_LOGICAL_BACKUP",
  "parametersJson": "{\"databaseName\":\"orders\",\"host\":\"127.0.0.1\",\"port\":3306,\"username\":\"backup_user\",\"password\":\"secret\",\"backupType\":\"LOGICAL\",\"compression\":true}"
}
```

### Response

```json
{
  "taskId": 15,
  "agentId": 1,
  "operationJobId": 100,
  "taskType": "MYSQL_LOGICAL_BACKUP",
  "status": "QUEUED",
  "credentialId": 7,
  "parametersJson": "{\"databaseName\":\"orders\",\"host\":\"127.0.0.1\",\"port\":3306,\"backupType\":\"LOGICAL\",\"compression\":true}",
  "resultPayloadJson": null,
  "errorCode": null,
  "errorMessage": null,
  "startedAt": null,
  "completedAt": null,
  "executionAttempt": 0,
  "leaseExpiresAt": null,
  "lastProgressAt": null,
  "createdAt": "2026-07-06T17:30:00"
}
```

### 설명

기존에는 Agent가 가져가는 작업을 `AgentTask`로 표현했습니다.

현재는 작업의 성격을 더 명확히 하기 위해 `OperationTask`로 변경했습니다.

`OperationTask`는 Agent 자체의 속성이 아니라 `OperationJob`의 하위 실행 단위입니다.

```text
OperationJob
  ↓
OperationTask
  ↓
Go Agent
```

`operationJobId`가 있는 Task는 특정 OperationJob에서 파생된 작업입니다.

---

## 4. 다음 OperationTask 선점

```http
POST /internal/v1/agents/{agentId}/tasks/next?agentToken={agentToken}
```

### Task 있음

```json
{
  "hasTask": true,
  "taskId": 15,
  "taskType": "MYSQL_LOGICAL_BACKUP",
  "parametersJson": "{\"databaseName\":\"orders\",\"host\":\"127.0.0.1\",\"port\":3306,\"username\":\"backup_user\",\"password\":\"secret\",\"backupType\":\"LOGICAL\",\"compression\":true}",
  "executionAttempt": 1,
  "leaseExpiresAt": "2026-07-06T17:32:00"
}
```

### Task 없음

```json
{
  "hasTask": false,
  "taskId": null,
  "taskType": null,
  "parametersJson": null,
  "executionAttempt": null,
  "leaseExpiresAt": null
}
```

### 설명

Go Agent는 주기적으로 다음 Task를 조회하고 즉시 선점합니다. 조회와 선점은 하나의 DB 트랜잭션에서 처리되므로 두 요청이 같은 Task를 받을 수 없습니다.

현재는 즉시 응답 방식이며, 실제 Long Polling 대기 처리는 아직 구현하지 않았습니다.

처리 기준은 다음과 같습니다.

```text
agentId 일치
  ↓
status = QUEUED
  ↓
createdAt 오름차순
  ↓
가장 오래된 Task 1건을 Lock
  ↓
RUNNING 전환, executionAttempt 증가, 60초 Lease 설정
  ↓
선점한 Task 반환
```

---

## 5. OperationTask Lease 갱신

```http
POST /internal/v1/agents/{agentId}/tasks/{taskId}/lease
```

### Request

```json
{
  "agentToken": "agent-token-...",
  "executionAttempt": 1
}
```

### Response

```json
{
  "taskId": 15,
  "executionAttempt": 1,
  "leaseExpiresAt": "2026-07-06T17:32:20",
  "lastProgressAt": "2026-07-06T17:31:20"
}
```

### 설명

Go Agent는 실행 중인 Task의 Lease를 기본 20초마다 갱신합니다. Agent Token, Task 소유 Agent, 현재 실행 번호가 모두 일치하고 기존 Lease가 아직 유효할 때만 갱신됩니다. 이전 실행 번호나 만료된 Lease는 HTTP 409 `DBOPS-TASK-40901`을 반환합니다.

---

## 6. OperationTask 완료

```http
POST /internal/v1/agents/{agentId}/tasks/{taskId}/complete
```

### Linux 상태 수집 완료 Request

```json
{
  "agentToken": "agent-token-...",
  "executionAttempt": 1,
  "resultReportId": "8d77288c-cf64-4ae8-a5be-a4010192fc6e",
  "resultPayloadJson": "{\"cpuUsagePercent\":14.2,\"memoryUsagePercent\":53.7,\"diskUsagePercent\":61.4}"
}
```

### Linux 상태 수집 완료 Response

```json
{
  "taskId": 10,
  "agentId": 1,
  "operationJobId": null,
  "taskType": "COLLECT_LINUX_STATUS",
  "status": "SUCCEEDED",
  "parametersJson": "{}",
  "resultPayloadJson": "{\"cpuUsagePercent\":14.2,\"memoryUsagePercent\":53.7,\"diskUsagePercent\":61.4}",
  "errorCode": null,
  "errorMessage": null,
  "startedAt": "2026-07-06T17:31:00",
  "completedAt": "2026-07-06T17:31:01",
  "createdAt": "2026-07-06T17:30:00"
}
```

### MySQL 백업 완료 Request

```json
{
  "agentToken": "agent-token-...",
  "executionAttempt": 1,
  "resultReportId": "8d77288c-cf64-4ae8-a5be-a4010192fc6e",
  "resultPayloadJson": "{\"status\":\"VERIFIED\",\"backupFile\":\"/tmp/db-fleetops-backups/orders-20260706-173000.sql\",\"fileSizeBytes\":182731,\"checksumSha256\":\"5f70bf18a086007016ddcafcdb2934c567b38b354b77bb636c4f8f15e3f3c8ab\",\"createdAt\":\"2026-07-06T17:30:00+09:00\",\"message\":\"backup artifact verified\"}"
}
```

### MySQL 백업 완료 Response

```json
{
  "taskId": 15,
  "agentId": 1,
  "operationJobId": 100,
  "taskType": "MYSQL_LOGICAL_BACKUP",
  "status": "SUCCEEDED",
  "parametersJson": "{\"databaseName\":\"orders\",\"host\":\"127.0.0.1\",\"port\":3306,\"username\":\"backup_user\",\"password\":\"secret\",\"backupType\":\"LOGICAL\",\"compression\":true}",
  "resultPayloadJson": "{\"status\":\"VERIFIED\",\"backupFile\":\"/tmp/db-fleetops-backups/orders-20260706-173000.sql\",\"fileSizeBytes\":182731,\"checksumSha256\":\"5f70bf18a086007016ddcafcdb2934c567b38b354b77bb636c4f8f15e3f3c8ab\",\"createdAt\":\"2026-07-06T17:30:00+09:00\",\"message\":\"backup artifact verified\"}",
  "errorCode": null,
  "errorMessage": null,
  "startedAt": "2026-07-06T17:31:00",
  "completedAt": "2026-07-06T17:32:00",
  "createdAt": "2026-07-06T17:30:00"
}
```

### 설명

Task 완료 시 상태는 `SUCCEEDED`가 됩니다.

```text
RUNNING
  ↓
SUCCEEDED
```

`COLLECT_LINUX_STATUS` Task가 완료되면 `resultPayloadJson`을 파싱하여 `agent_host_metric` 테이블에 저장합니다.

`MYSQL_LOGICAL_BACKUP` Task가 완료되고 `operationJobId`가 존재하면 연결된 OperationJob도 `SUCCEEDED`로 변경됩니다.

---

## 7. OperationTask 실패

```http
POST /internal/v1/agents/{agentId}/tasks/{taskId}/fail
```

### Request

```json
{
  "agentToken": "agent-token-...",
  "executionAttempt": 1,
  "resultReportId": "3b42dbed-c531-4dc8-b235-e2945dd52c90",
  "errorCode": "MYSQL_BACKUP_FAILED",
  "errorMessage": "mysqldump exited with code 2"
}
```

### Response

```json
{
  "taskId": 15,
  "agentId": 1,
  "operationJobId": 100,
  "taskType": "MYSQL_LOGICAL_BACKUP",
  "status": "FAILED",
  "parametersJson": "{\"databaseName\":\"orders\",\"host\":\"127.0.0.1\",\"port\":3306,\"username\":\"backup_user\",\"password\":\"secret\",\"backupType\":\"LOGICAL\",\"compression\":true}",
  "resultPayloadJson": null,
  "errorCode": "MYSQL_BACKUP_FAILED",
  "errorMessage": "mysqldump exited with code 2",
  "startedAt": "2026-07-06T17:31:00",
  "completedAt": "2026-07-06T17:32:00",
  "createdAt": "2026-07-06T17:30:00"
}
```

### 설명

Task 실패 시 상태는 `FAILED`가 됩니다.

```text
RUNNING
  ↓
FAILED
```

완료와 실패 요청의 `resultReportId`는 Agent가 결과마다 한 번 생성한 UUID입니다. 같은 실행 번호, UUID, 결과 종류와 내용이 다시 오면 기존 결과를 HTTP 200으로 반환하고 Metric, Job 변경과 후속 Task 생성을 반복하지 않습니다. 하나라도 다르거나 Lease가 만료되었으면 HTTP 409를 반환합니다.

---

## 8. OperationTask Lease 운영 DDL

애플리케이션 배포 전에 운영 MySQL에 다음 DDL을 적용합니다. 기존 Task는 실행 전 상태와 호환되도록 실행 번호 0, Lease 시각 `NULL`로 시작합니다.

```sql
ALTER TABLE operation_task
    ADD COLUMN execution_attempt INT NOT NULL DEFAULT 0,
    ADD COLUMN lease_expires_at DATETIME(6) NULL,
    ADD COLUMN last_progress_at DATETIME(6) NULL;

ALTER TABLE operation_task
    ADD COLUMN result_report_id VARCHAR(36) NULL,
    ADD COLUMN result_report_type VARCHAR(16) NULL,
    ADD COLUMN result_report_fingerprint CHAR(64) NULL,
    ADD COLUMN credential_id BIGINT NULL;

ALTER TABLE managed_database
    ADD COLUMN assigned_agent_id BIGINT NULL;

CREATE INDEX idx_operation_task_status_lease_expires_at
    ON operation_task (status, lease_expires_at);

CREATE INDEX idx_operation_task_credential_id ON operation_task (credential_id);
CREATE INDEX idx_managed_database_assigned_agent_id
    ON managed_database (assigned_agent_id);
```

Lease가 만료되면 최초 실행을 포함해 최대 3회까지 같은 Task ID를 다시 `QUEUED`로 전환합니다. 세 번째 실행도 만료되면 Task와 실행 중인 상위 OperationJob을 `TIMED_OUT`으로 종료합니다.

`operationJobId`가 존재하면 연결된 OperationJob도 `FAILED`로 변경됩니다.

---

## 지원 Task Type

| Task Type | 설명 |
|---|---|
| COLLECT_LINUX_STATUS | Linux Host 상태 수집 |
| MYSQL_LOGICAL_BACKUP | MySQL 논리 백업 실행 및 백업 산출물 검증 |

---

## MySQL 백업 처리 흐름

현재 Go Agent의 `MYSQL_LOGICAL_BACKUP` Handler는 실제 `mysqldump`를 실행합니다.

```text
MYSQL_LOGICAL_BACKUP Task 수신
  ↓
parametersJson 파싱
  ↓
mysqldump 실행
  ↓
.sql 백업 파일 생성
  ↓
파일 존재 확인
  ↓
파일 크기 확인
  ↓
SHA-256 checksum 생성
  ↓
MySQL dump header 확인
  ↓
VERIFIED 결과 반환
```

### 백업 결과 예시

```json
{
  "status": "VERIFIED",
  "backupFile": "/tmp/db-fleetops-backups/orders-20260706-173000.sql",
  "fileSizeBytes": 182731,
  "checksumSha256": "5f70bf18a086007016ddcafcdb2934c567b38b354b77bb636c4f8f15e3f3c8ab",
  "createdAt": "2026-07-06T17:30:00+09:00",
  "message": "backup artifact verified"
}
```

---

## Agent Local State

Go Agent는 최초 등록 후 다음 정보를 Local State 파일에 저장합니다.

```json
{
  "agentId": 1,
  "agentToken": "agent-token-..."
}
```

현재 기본 저장 위치는 다음과 같습니다.

```text
agent-go/agent-state.json
```

운영 환경에서는 systemd service 경로나 `/var/lib/db-fleet-agent/agent-state.json` 같은 위치로 변경할 예정입니다.

---

## Task Credential 조회

```http
POST /internal/v1/agents/{agentId}/tasks/{taskId}/credential
```

```json
{
  "agentToken": "agent-token-...",
  "executionAttempt": 1
}
```

Control Plane은 현재 Task를 실행할 권한이 있는 Agent에만 username과 password를 반환합니다. Task 소유 Agent, 실행 번호, 유효한 Lease와 Database의 `assignedAgentId`를 모두 확인하며, 조건이 맞지 않으면 HTTP 409를 반환합니다. Credential은 Task Payload, 일반 Database 응답, Audit와 오류 응답에 넣지 않습니다.

Credential 비밀번호 컬럼에는 AES-256-GCM 결과인 `v1:<nonce>:<ciphertext>`만 저장합니다. 배포 전에 기존 평문 Credential을 새 API로 다시 저장하고 비밀번호가 포함된 기존 대기 Task를 정리해야 합니다. 암호화 Key는 `DB_FLEETOPS_CREDENTIAL_ENCRYPTION_KEY`에 Base64 32-byte 값으로 제공합니다.

## 현재 한계

아직 다음 기능은 없습니다.

- mTLS
- Agent Token 암호화 저장
- Authorization Header 기반 인증
- 실제 Long Polling 대기 처리
- 백업 압축 처리
- 백업 파일 외부 저장소 업로드
- Database별 대체 Agent 자동 선택
- Agent 재시작 뒤 Credential/결과 보고 Outbox 복구
