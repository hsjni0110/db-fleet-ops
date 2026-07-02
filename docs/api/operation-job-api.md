# Operation Job API

## 개요

Operation Job API는 오래 걸리거나 실패할 수 있는 DB 운영 작업을 Job으로 생성하고, Worker가 이를 가져가 처리하기 위한 API입니다.

현재는 실제 백업 실행까지 구현하지 않고, Job Engine 흐름을 먼저 구현했습니다.

구현 범위는 다음과 같습니다.

- Backup Job 생성
- Job 조회
- Worker Claim
- Job 성공 처리
- Job 실패 처리
- Retry 처리
- Audit Log 기록

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
  "availableAt": "2026-07-02T22:10:00",
  "startedAt": null,
  "finishedAt": null,
  "resultCode": null,
  "resultMessage": null,
  "createdAt": "2026-07-02T22:10:00"
}
```

### 설명

요청이 접수되면 실제 작업을 즉시 실행하지 않고 `QUEUED` 상태의 Job을 생성합니다.

같은 `databaseId`, `jobType`, `Idempotency-Key` 조합의 Job이 이미 존재하면 새로 만들지 않고 기존 Job을 반환합니다.

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
  "maxRetryCount": 3
}
```

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
  "leaseUntil": "2026-07-02T22:11:00"
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
  "leaseOwner": "worker-1",
  "resultCode": "SUCCESS",
  "resultMessage": "backup completed"
}
```

### 설명

Job을 Claim한 Worker만 성공 처리할 수 있습니다.

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
  "resultCode": "BACKUP_FAILED",
  "resultMessage": "mysqldump failed"
}
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
  "retryCount": 1,
  "maxRetryCount": 3,
  "leaseOwner": null,
  "leaseUntil": null,
  "resultCode": "BACKUP_FAILED",
  "resultMessage": "temporary mysqldump error"
}
```

### 설명

`retryable=true`이고 `retryCount < maxRetryCount`이면 Job을 다시 `QUEUED` 상태로 되돌립니다.

---

## 7. 현재 상태값

```text
QUEUED
RUNNING
SUCCEEDED
FAILED
CANCELLED
TIMED_OUT
```

---

## 8. Audit 기록 이벤트

현재 Operation Job 흐름에서 기록하는 Audit 이벤트는 다음과 같습니다.

| Action | 발생 시점 |
|---|---|
| JOB_CREATED | Job 생성 |
| JOB_CLAIMED | Worker Claim |
| JOB_SUCCEEDED | Job 성공 처리 |
| JOB_FAILED | Job 실패 처리 |
| JOB_RETRIED | Retry로 다시 QUEUED 처리 |
