# Restore Verification API

## 개요

Restore Verification API는 DB FleetOps에서 수행한 백업 파일이 실제로 복원 가능한지 검증한 결과를 조회하기 위한 API입니다.

기존 백업 성공 기준은 백업 파일 생성 여부였습니다.  
Restore Verification 기능이 추가되면서 백업 성공 기준은 다음과 같이 확장되었습니다.

```text
백업 파일 생성
  ↓
임시 DB에 실제 복원
  ↓
테이블 존재 여부 확인
  ↓
Row Count 검증
  ↓
복원 검증 결과 저장
```

현재 지원 DBMS

- MySQL

---

# 1. Job 기준 복원 검증 결과 조회

특정 백업 Job에 연결된 복원 검증 결과를 조회합니다.

## Request

```http
GET /api/v1/jobs/{jobId}/restore-verification
```

### Path Variable

| 이름 | 설명 |
|---|---|
| jobId | OperationJob ID |

---

## Response

```json
{
  "id": 400,
  "operationJobId": 100,
  "backupTaskId": 200,
  "restoreVerifyTaskId": 300,
  "databaseId": 1,
  "sourceDatabaseName": "orders",
  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
  "temporaryDatabaseName": "restore_verify_orders_100",
  "status": "VERIFIED",
  "restoredTableCount": 2,
  "checkedTableCount": 2,
  "totalRowCount": 38512,
  "errorCode": null,
  "errorMessage": null,
  "startedAt": "2026-07-08T14:00:00",
  "completedAt": "2026-07-08T14:01:00",
  "createdAt": "2026-07-08T13:59:50",
  "items": [
    {
      "id": 1,
      "verificationId": 400,
      "tableName": "orders",
      "existsInRestoredDb": true,
      "rowCount": 12000,
      "status": "VERIFIED",
      "message": "table verified",
      "createdAt": "2026-07-08T14:01:00"
    },
    {
      "id": 2,
      "verificationId": 400,
      "tableName": "order_items",
      "existsInRestoredDb": true,
      "rowCount": 26512,
      "status": "VERIFIED",
      "message": "table verified",
      "createdAt": "2026-07-08T14:01:00"
    }
  ]
}
```

---

# 2. DB 기준 최신 복원 검증 결과 조회

특정 DB 인스턴스의 가장 최근 복원 검증 결과를 조회합니다.

## Request

```http
GET /api/v1/databases/{databaseId}/restore-verifications/latest
```

### Path Variable

| 이름 | 설명 |
|---|---|
| databaseId | 관리 대상 DB ID |

---

## Response

```json
{
  "id": 400,
  "operationJobId": 100,
  "backupTaskId": 200,
  "restoreVerifyTaskId": 300,
  "databaseId": 1,
  "sourceDatabaseName": "orders",
  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
  "temporaryDatabaseName": "restore_verify_orders_100",
  "status": "VERIFIED",
  "restoredTableCount": 2,
  "checkedTableCount": 2,
  "totalRowCount": 38512,
  "errorCode": null,
  "errorMessage": null,
  "startedAt": "2026-07-08T14:00:00",
  "completedAt": "2026-07-08T14:01:00",
  "createdAt": "2026-07-08T13:59:50",
  "items": [
    {
      "id": 1,
      "verificationId": 400,
      "tableName": "orders",
      "existsInRestoredDb": true,
      "rowCount": 12000,
      "status": "VERIFIED",
      "message": "table verified",
      "createdAt": "2026-07-08T14:01:00"
    },
    {
      "id": 2,
      "verificationId": 400,
      "tableName": "order_items",
      "existsInRestoredDb": true,
      "rowCount": 26512,
      "status": "VERIFIED",
      "message": "table verified",
      "createdAt": "2026-07-08T14:01:00"
    }
  ]
}
```

---

# 3. 복원 검증 상세 조회

복원 검증 ID를 기준으로 상세 결과를 조회합니다.

## Request

```http
GET /api/v1/restore-verifications/{verificationId}
```

### Path Variable

| 이름 | 설명 |
|---|---|
| verificationId | BackupRestoreVerification ID |

---

## Response

```json
{
  "id": 400,
  "operationJobId": 100,
  "backupTaskId": 200,
  "restoreVerifyTaskId": 300,
  "databaseId": 1,
  "sourceDatabaseName": "orders",
  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
  "temporaryDatabaseName": "restore_verify_orders_100",
  "status": "VERIFIED",
  "restoredTableCount": 2,
  "checkedTableCount": 2,
  "totalRowCount": 38512,
  "errorCode": null,
  "errorMessage": null,
  "startedAt": "2026-07-08T14:00:00",
  "completedAt": "2026-07-08T14:01:00",
  "createdAt": "2026-07-08T13:59:50",
  "items": [
    {
      "id": 1,
      "verificationId": 400,
      "tableName": "orders",
      "existsInRestoredDb": true,
      "rowCount": 12000,
      "status": "VERIFIED",
      "message": "table verified",
      "createdAt": "2026-07-08T14:01:00"
    },
    {
      "id": 2,
      "verificationId": 400,
      "tableName": "order_items",
      "existsInRestoredDb": true,
      "rowCount": 26512,
      "status": "VERIFIED",
      "message": "table verified",
      "createdAt": "2026-07-08T14:01:00"
    }
  ]
}
```

---

# 4. 상태값

## Restore Verification Status

| 상태 | 설명 |
|---|---|
| REQUESTED | 복원 검증 요청이 생성된 상태 |
| RUNNING | 복원 검증이 진행 중인 상태 |
| VERIFIED | 백업 파일이 임시 DB에 복원되었고 최소 검증을 통과한 상태 |
| FAILED | 복원 또는 검증에 실패한 상태 |
| CLEANUP_FAILED | 복원 검증은 수행됐지만 임시 DB 삭제에 실패한 상태 |

---

## Restore Verification Item Status

| 상태 | 설명 |
|---|---|
| VERIFIED | 테이블 존재 확인 및 Row Count 검증이 완료된 상태 |
| MISSING | 기대한 테이블이 복원된 DB에 없는 상태 |
| COUNT_FAILED | 테이블은 존재하지만 Row Count 조회에 실패한 상태 |
| SKIPPED | Row Count 검증을 수행하지 않은 상태 |

---

# 5. 주요 필드 설명

| 필드 | 설명 |
|---|---|
| id | 복원 검증 결과 ID |
| operationJobId | 백업 OperationJob ID |
| backupTaskId | MYSQL_LOGICAL_BACKUP Task ID |
| restoreVerifyTaskId | MYSQL_RESTORE_VERIFY Task ID |
| databaseId | 관리 대상 DB ID |
| sourceDatabaseName | 원본 DB 이름 |
| backupFile | 복원 검증에 사용한 백업 파일 경로 |
| temporaryDatabaseName | 복원 검증을 위해 생성한 임시 DB 이름 |
| status | 복원 검증 상태 |
| restoredTableCount | 복원된 DB에서 확인된 테이블 수 |
| checkedTableCount | 검증을 수행한 테이블 수 |
| totalRowCount | 검증 대상 테이블의 전체 Row Count 합계 |
| errorCode | 실패 코드 |
| errorMessage | 실패 메시지 |
| startedAt | 복원 검증 시작 시각 |
| completedAt | 복원 검증 완료 시각 |
| createdAt | 결과 생성 시각 |
| items | 테이블 단위 검증 결과 목록 |

---

# 6. 실패 응답 예시

복원은 수행됐지만 기대한 테이블이 없거나 Row Count 검증에 실패하면 `FAILED` 상태로 저장됩니다.

```json
{
  "id": 401,
  "operationJobId": 101,
  "backupTaskId": 201,
  "restoreVerifyTaskId": 301,
  "databaseId": 1,
  "sourceDatabaseName": "orders",
  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
  "temporaryDatabaseName": "restore_verify_orders_101",
  "status": "FAILED",
  "restoredTableCount": 1,
  "checkedTableCount": 1,
  "totalRowCount": 12000,
  "errorCode": "RESTORE_VERIFY_FAILED",
  "errorMessage": "one or more restored table checks failed",
  "startedAt": "2026-07-08T14:10:00",
  "completedAt": "2026-07-08T14:11:00",
  "createdAt": "2026-07-08T14:09:50",
  "items": [
    {
      "id": 3,
      "verificationId": 401,
      "tableName": "order_items",
      "existsInRestoredDb": false,
      "rowCount": null,
      "status": "MISSING",
      "message": "expected table is missing in restored database",
      "createdAt": "2026-07-08T14:11:00"
    }
  ]
}
```

---

# 7. Cleanup 실패 응답 예시

복원 검증은 완료됐지만 임시 DB 삭제에 실패하면 `CLEANUP_FAILED` 상태로 저장됩니다.

```json
{
  "id": 402,
  "operationJobId": 102,
  "backupTaskId": 202,
  "restoreVerifyTaskId": 302,
  "databaseId": 1,
  "sourceDatabaseName": "orders",
  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
  "temporaryDatabaseName": "restore_verify_orders_102",
  "status": "CLEANUP_FAILED",
  "restoredTableCount": 2,
  "checkedTableCount": 2,
  "totalRowCount": 38512,
  "errorCode": "CLEANUP_FAILED",
  "errorMessage": "drop database failed",
  "startedAt": "2026-07-08T14:20:00",
  "completedAt": "2026-07-08T14:21:00",
  "createdAt": "2026-07-08T14:19:50",
  "items": [
    {
      "id": 4,
      "verificationId": 402,
      "tableName": "orders",
      "existsInRestoredDb": true,
      "rowCount": 12000,
      "status": "VERIFIED",
      "message": "table verified",
      "createdAt": "2026-07-08T14:21:00"
    },
    {
      "id": 5,
      "verificationId": 402,
      "tableName": "order_items",
      "existsInRestoredDb": true,
      "rowCount": 26512,
      "status": "VERIFIED",
      "message": "table verified",
      "createdAt": "2026-07-08T14:21:00"
    }
  ]
}
```

---

# 8. 운영 흐름

```text
MYSQL_LOGICAL_BACKUP Task 완료
  ↓
backupFile 생성 확인
  ↓
MYSQL_RESTORE_VERIFY Task 생성
  ↓
임시 DB 생성
  ↓
backupFile restore
  ↓
SHOW TABLES
  ↓
expectedTables 존재 여부 확인
  ↓
SELECT COUNT(*) FROM table
  ↓
임시 DB 삭제
  ↓
BackupRestoreVerification 저장
  ↓
OperationJob SUCCEEDED 또는 FAILED 처리
```

---

# 9. Job 처리 정책

| Restore Verification 결과 | OperationJob 결과 | 설명 |
|---|---|---|
| VERIFIED | SUCCEEDED | 백업 파일이 실제로 복원 가능하다고 판단 |
| FAILED | FAILED | 복원 또는 검증 실패 |
| CLEANUP_FAILED | FAILED | 임시 DB 삭제 실패로 운영상 불완전 종료 |
| verifyAfterBackup=false | SUCCEEDED | 복원 검증을 생략하고 백업 파일 생성 결과만 반영 |

---

# 10. 설계 의도

Restore Verification은 단순히 백업 파일이 생성됐는지를 확인하는 기능이 아닙니다.

백업 파일이 실제로 임시 DB에 복원 가능한지 확인하고, 최소한의 테이블 존재 여부와 Row Count를 검증합니다.  
이를 통해 백업 성공 기준을 파일 생성 여부에서 복원 가능성 중심으로 확장했습니다.

이 구조는 운영 환경에서 다음 문제를 줄이기 위한 목적이 있습니다.

- 백업 파일은 존재하지만 실제 복원이 실패하는 문제
- 특정 테이블이 누락된 백업을 성공으로 판단하는 문제
- 복원 검증 이력이 남지 않아 장애 대응 시 근거가 부족한 문제
- 백업 Job 성공 기준이 명확하지 않은 문제