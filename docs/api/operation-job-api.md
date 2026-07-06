# Operation Job API

## 개요

Operation Job API는 오래 걸리거나 실패할 수 있는 DB 운영 작업을 Job으로 생성하고, Worker와 Agent를 통해 비동기로 처리하기 위한 API입니다.

현재 구현 범위는 다음과 같습니다.

- Backup Job 생성
- Job 조회
- Worker Claim
- Lease 설정
- Retry 처리
- Audit Log 기록
- OperationTask 생성
- Go Agent Polling
- MySQL 논리 백업 실행
- 백업 산출물 검증
- OperationTask 결과를 OperationJob에 반영

---

## OperationJob과 OperationTask 관계

현재 구조에서는 `OperationJob`과 `OperationTask`를 분리합니다.

```text
OperationJob
  ↓
OperationTask
  ↓
Go Agent
```

각 책임은 다음과 같습니다.

| 구분 | 책임 |
|---|---|
| OperationJob | 운영 작업의 상위 단위, 요청자·대상 DB·상태·Retry·Lease 관리 |
| OperationTask | 실제 Agent 또는 Worker가 실행할 하위 작업 단위 |
| Go Agent | OperationTask를 Polling하여 실제 Host 작업 수행 |

예를 들어 Backup Job은 다음 흐름으로 실행됩니다.

```text
Backup Job 생성
  ↓
Worker Claim
  ↓
MYSQL_LOGICAL_BACKUP OperationTask 생성
  ↓
Go Agent가 Task Polling
  ↓
mysqldump 실행
  ↓
백업 산출물 검증
  ↓
OperationTask SUCCEEDED
  ↓
OperationJob SUCCEEDED
```

---

## 1. Backup Job 생성

```http
POST /api/v1/database-instances/{databaseId}/operations/backups
```

### Header

```http
Content-Type: application/json
Idempotency-Key: idem-backup-001
```

### Request Body

```json
{
  "reason": "manual backup before deployment",
  "requestedBy": "local-user"
}
```

### Response

```json
{
  "jobId": 1,
  "jobType": "BACKUP",
  "status": "QUEUED",
  "targetDatabaseId": 1,
  "requestedBy": "local-user",
  "retryCount": 0,
  "maxRetryCount": 3,
  "leaseOwner": null,
  "leaseUntil": null,
  "availableAt": "2026-07-06T17:30:00",
  "startedAt": null,
  "finishedAt": null,
  "resultCode": null,
  "resultMessage": null,
  "createdAt": "2026-07-06T17:30:00"
}
```

### 설명

요청이 접수되면 실제 백업을 즉시 실행하지 않고 `QUEUED` 상태의 Job을 생성합니다.

같은 `databaseId`, `jobType`, `Idempotency-Key` 조합의 Job이 이미 존재하면 새로 만들지 않고 기존 Job을 반환합니다.

이 구조는 사용자가 같은 요청을 여러 번 보내더라도 중복 백업이 실행되지 않도록 하기 위한 것입니다.

---

## 2. Job 조회

```http
GET /api/v1/jobs/{jobId}
```

### Response

```json
{
  "jobId": 1,
  "jobType": "BACKUP",
  "status": "QUEUED",
  "targetDatabaseId": 1,
  "requestedBy": "local-user",
  "retryCount": 0,
  "maxRetryCount": 3,
  "leaseOwner": null,
  "leaseUntil": null,
  "availableAt": "2026-07-06T17:30:00",
  "startedAt": null,
  "finishedAt": null,
  "resultCode": null,
  "resultMessage": null,
  "createdAt": "2026-07-06T17:30:00"
}
```

### 설명

Job의 현재 상태를 조회합니다.

사용자는 이 API를 통해 백업 요청이 대기 중인지, 실행 중인지, 성공했는지, 실패했는지 확인할 수 있습니다.

---

## 3. Worker Job Claim

```http
POST /internal/v1/workers/{workerId}/jobs/claim
```

### Response: Job이 있는 경우

```json
{
  "claimed": true,
  "jobId": 1,
  "jobType": "BACKUP",
  "status": "RUNNING",
  "targetDatabaseId": 1,
  "leaseOwner": "worker-1",
  "leaseUntil": "2026-07-06T17:31:00"
}
```

### Response: Job이 없는 경우

```json
{
  "claimed": false,
  "jobId": null,
  "jobType": null,
  "status": null,
  "targetDatabaseId": null,
  "leaseOwner": null,
  "leaseUntil": null
}
```

### 설명

Worker가 `QUEUED` 상태의 Job 하나를 가져가 `RUNNING`으로 변경합니다.

이때 `leaseOwner`와 `leaseUntil`을 설정합니다.

```text
QUEUED
  ↓ claim
RUNNING
```

현재 `BACKUP` Job을 Claim하면 내부적으로 `MYSQL_LOGICAL_BACKUP` OperationTask가 자동 생성됩니다.

```text
Worker Claim
  ↓
OperationJob RUNNING
  ↓
OperationTask MYSQL_LOGICAL_BACKUP 생성
```

현재 Agent 선택 방식은 MVP 기준으로 가장 최근 Heartbeat가 들어온 `ONLINE` Agent를 선택합니다.

---

## 4. Job 성공 처리

```http
POST /internal/v1/workers/{workerId}/jobs/{jobId}/succeed
```

### Request Body

```json
{
  "resultMessage": "backup completed"
}
```

### Response

```json
{
  "jobId": 1,
  "jobType": "BACKUP",
  "status": "SUCCEEDED",
  "targetDatabaseId": 1,
  "requestedBy": "local-user",
  "retryCount": 0,
  "maxRetryCount": 3,
  "leaseOwner": "worker-1",
  "leaseUntil": "2026-07-06T17:31:00",
  "availableAt": "2026-07-06T17:30:00",
  "startedAt": "2026-07-06T17:30:10",
  "finishedAt": "2026-07-06T17:32:00",
  "resultCode": "SUCCESS",
  "resultMessage": "backup completed",
  "createdAt": "2026-07-06T17:30:00"
}
```

### 설명

Job을 Claim한 Worker만 성공 처리할 수 있습니다.

다만 현재 백업 흐름에서는 Worker가 직접 성공 처리하기보다, 연결된 OperationTask가 성공하면 OperationJob도 자동으로 `SUCCEEDED` 처리됩니다.

```text
OperationTask SUCCEEDED
  ↓
OperationJob SUCCEEDED
```

---

## 5. Job 실패 처리

```http
POST /internal/v1/workers/{workerId}/jobs/{jobId}/fail
```

### Request Body

```json
{
  "resultCode": "BACKUP_FAILED",
  "resultMessage": "mysqldump failed",
  "retryable": false
}
```

### Response

```json
{
  "jobId": 1,
  "jobType": "BACKUP",
  "status": "FAILED",
  "targetDatabaseId": 1,
  "requestedBy": "local-user",
  "retryCount": 0,
  "maxRetryCount": 3,
  "leaseOwner": "worker-1",
  "leaseUntil": "2026-07-06T17:31:00",
  "availableAt": "2026-07-06T17:30:00",
  "startedAt": "2026-07-06T17:30:10",
  "finishedAt": "2026-07-06T17:32:00",
  "resultCode": "BACKUP_FAILED",
  "resultMessage": "mysqldump failed",
  "createdAt": "2026-07-06T17:30:00"
}
```

### 설명

Job을 Claim한 Worker만 실패 처리할 수 있습니다.

OperationTask가 실패한 경우에도 연결된 OperationJob은 실패 처리됩니다.

```text
OperationTask FAILED
  ↓
OperationJob FAILED
```

---

## 6. Retry 가능한 실패 처리

```http
POST /internal/v1/workers/{workerId}/jobs/{jobId}/fail
```

### Request Body

```json
{
  "resultCode": "BACKUP_FAILED",
  "resultMessage": "temporary mysqldump error",
  "retryable": true
}
```

### Response

```json
{
  "jobId": 1,
  "jobType": "BACKUP",
  "status": "QUEUED",
  "targetDatabaseId": 1,
  "requestedBy": "local-user",
  "retryCount": 1,
  "maxRetryCount": 3,
  "leaseOwner": null,
  "leaseUntil": null,
  "availableAt": "2026-07-06T17:32:30",
  "startedAt": "2026-07-06T17:30:10",
  "finishedAt": "2026-07-06T17:32:00",
  "resultCode": "BACKUP_FAILED",
  "resultMessage": "temporary mysqldump error",
  "createdAt": "2026-07-06T17:30:00"
}
```

### 설명

`retryable=true`이고 `retryCount < maxRetryCount`이면 Job을 다시 `QUEUED` 상태로 되돌립니다.

```text
RUNNING
  ↓ fail
FAILED
  ↓ retry
QUEUED
```

Retry 시에는 다음 값이 초기화됩니다.

- `leaseOwner`
- `leaseUntil`

그리고 `availableAt`은 일정 시간 뒤로 설정됩니다.

---

## 7. OperationTask 생성 흐름

Backup Job은 Worker Claim 시점에 OperationTask를 생성합니다.

```text
OperationJob(BACKUP, QUEUED)
  ↓
Worker Claim
  ↓
OperationJob(BACKUP, RUNNING)
  ↓
OperationTask(MYSQL_LOGICAL_BACKUP, QUEUED)
```

생성되는 OperationTask 예시는 다음과 같습니다.

```json
{
  "taskId": 15,
  "agentId": 1,
  "operationJobId": 1,
  "taskType": "MYSQL_LOGICAL_BACKUP",
  "status": "QUEUED",
  "parametersJson": "{\"operationJobId\":1,\"databaseId\":1,\"backupType\":\"LOGICAL\",\"compression\":true}",
  "resultPayloadJson": null,
  "errorCode": null,
  "errorMessage": null,
  "startedAt": null,
  "completedAt": null,
  "createdAt": "2026-07-06T17:30:10"
}
```

현재는 `ManagedDatabase`와 `Agent`가 직접 연결되어 있지 않기 때문에 가장 최근 Heartbeat가 들어온 `ONLINE` Agent를 선택합니다.

향후에는 다음 구조로 개선할 예정입니다.

```text
ManagedDatabase
  ↓
assignedAgentId
  ↓
Agent
```

---

## 8. Backup Job 전체 실행 흐름

```text
POST /api/v1/database-instances/{databaseId}/operations/backups
  ↓
OperationJob QUEUED 생성
  ↓
POST /internal/v1/workers/{workerId}/jobs/claim
  ↓
OperationJob RUNNING
  ↓
OperationTask MYSQL_LOGICAL_BACKUP QUEUED 생성
  ↓
Go Agent Polling
  ↓
OperationTask RUNNING
  ↓
mysqldump 실행
  ↓
백업 파일 생성
  ↓
백업 산출물 검증
  ↓
OperationTask SUCCEEDED
  ↓
OperationJob SUCCEEDED
```

---

## 9. 백업 산출물 검증

현재 Go Agent는 `mysqldump` 실행 후 바로 성공 처리하지 않습니다.

다음 최소 검증을 수행합니다.

| 검증 항목 | 설명 |
|---|---|
| 파일 존재 | 백업 파일이 실제 생성되었는지 확인 |
| 파일 크기 | 0 Byte 파일 여부 확인 |
| SHA-256 | 백업 파일 checksum 생성 |
| Dump Header | MySQL 또는 MariaDB dump 출력인지 확인 |

검증 성공 시 OperationTask 결과는 다음과 같습니다.

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

## 10. 현재 상태값

### OperationJob Status

```text
QUEUED
RUNNING
SUCCEEDED
FAILED
CANCELLED
TIMED_OUT
```

### OperationTask Status

```text
QUEUED
RUNNING
SUCCEEDED
FAILED
CANCELLED
```

---

## 11. Audit 기록 이벤트

현재 Operation Job 흐름에서 기록하는 Audit 이벤트는 다음과 같습니다.

| Action | 발생 시점 |
|---|---|
| JOB_CREATED | Job 생성 |
| JOB_CLAIMED | Worker Claim |
| OPERATION_TASK_CREATED | Backup Job Claim 후 OperationTask 생성 |
| JOB_SUCCEEDED | Job 성공 처리 |
| JOB_FAILED | Job 실패 처리 |
| JOB_RETRIED | Retry로 다시 QUEUED 처리 |

Task Start, Complete, Fail에 대한 별도 Audit은 아직 구현하지 않았습니다.

---

## 12. 현재 구현 범위

현재 구현 완료 범위는 다음과 같습니다.

- Backup Job 생성
- Idempotency-Key 기반 중복 생성 방지
- Worker Claim
- Lease 설정
- Job 성공 처리
- Job 실패 처리
- Retry 처리
- Audit 기록
- OperationTask 생성
- Go Agent Task Polling
- Go Agent Runtime Loop
- Go Agent Local State
- Linux Host Metric 수집
- AgentHostMetric 저장
- 실제 mysqldump 실행
- Backup Artifact Verify
- OperationTask 결과를 OperationJob에 반영

---

## 13. 현재 한계

아직 다음 기능은 없습니다.

- ManagedDatabase와 Agent 직접 매핑
- Task 동시 Claim 제어
- OperationTask 중복 생성 방지
- OperationTask 실패와 OperationJob Retry의 정교한 연결
- Credential Reference
- Secret Store 연동
- Restore Verification
- 백업 압축
- 백업 파일 외부 저장소 업로드
- OperationTask 단계별 Step 관리
- Long Polling
- mTLS 인증