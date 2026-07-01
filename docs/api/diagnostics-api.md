# MySQL Diagnostics API

## 개요

Diagnostics API는 등록된 DB 인스턴스를 기준으로 MySQL 운영 진단 정보를 조회하는 API입니다.

이 API는 임의 SQL 실행 기능을 제공하지 않습니다.  
정해진 진단 항목만 조회하여 DB 운영 상태를 확인합니다.

Base Path는 다음과 같습니다.

```http
/api/v1/database-instances/{databaseId}/diagnostics
```

---

## 공통 전제

요청한 `databaseId`는 DB FleetOps Inventory에 등록되어 있어야 합니다.

진단 처리 흐름은 다음과 같습니다.

```text
databaseId 조회
  ↓
ManagedDatabase 조회
  ↓
ACTIVE 상태 확인
  ↓
Credential 조회
  ↓
DatabaseEngine 기준 Diagnostic Port 선택
  ↓
MySQL Diagnostic Adapter 실행
  ↓
응답 반환
```

---

# 1. Version 조회

## Request

```http
GET /api/v1/database-instances/{databaseId}/diagnostics/version
```

## Response

```json
{
  "databaseId": 1,
  "engine": "MYSQL",
  "version": "8.4.0"
}
```

## 설명

대상 MySQL 인스턴스의 버전을 조회합니다.

사용 쿼리:

```sql
SELECT VERSION()
```

---

# 2. Uptime 조회

## Request

```http
GET /api/v1/database-instances/{databaseId}/diagnostics/uptime
```

## Response

```json
{
  "databaseId": 1,
  "engine": "MYSQL",
  "uptimeSeconds": 3600
}
```

## 설명

MySQL 서버가 마지막으로 시작된 이후 경과 시간을 초 단위로 조회합니다.

사용 쿼리:

```sql
SHOW GLOBAL STATUS LIKE 'Uptime'
```

---

# 3. Connection Summary 조회

## Request

```http
GET /api/v1/database-instances/{databaseId}/diagnostics/connections
```

## Response

```json
{
  "databaseId": 1,
  "engine": "MYSQL",
  "currentConnections": 12,
  "runningConnections": 2,
  "maxConnections": 151,
  "usagePercent": 7.95
}
```

## 설명

현재 연결 수, 실행 중인 연결 수, 최대 연결 수, 연결 사용률을 조회합니다.

사용 값:

```text
Threads_connected
Threads_running
max_connections
```

계산식:

```text
usagePercent = currentConnections / maxConnections * 100
```

---

# 4. Session 목록 조회

## Request

```http
GET /api/v1/database-instances/{databaseId}/diagnostics/sessions
```

## Response

```json
[
  {
    "databaseId": 1,
    "engine": "MYSQL",
    "processId": 10,
    "user": "db_monitor",
    "host": "localhost:50000",
    "databaseName": "orders",
    "command": "Query",
    "timeSeconds": 3,
    "state": "executing",
    "queryPreview": "SELECT * FROM orders"
  }
]
```

## 설명

현재 MySQL 세션 목록을 조회합니다.

사용 쿼리:

```sql
SELECT
    ID,
    USER,
    HOST,
    DB,
    COMMAND,
    TIME,
    STATE,
    INFO
FROM information_schema.PROCESSLIST
ORDER BY TIME DESC
```

`queryPreview`는 SQL 전체를 노출하지 않도록 길이를 제한합니다.

---

# 5. Long Transaction 조회

## Request

```http
GET /api/v1/database-instances/{databaseId}/diagnostics/long-transactions
```

## Response

```json
[
  {
    "databaseId": 1,
    "engine": "MYSQL",
    "transactionId": "12345",
    "state": "RUNNING",
    "startedAt": "2026-07-01T15:30:00",
    "durationSeconds": 120,
    "threadId": 10,
    "queryPreview": "UPDATE orders SET status = 'PAID'"
  }
]
```

## 설명

30초 이상 실행 중인 InnoDB Transaction을 조회합니다.

사용 쿼리:

```sql
SELECT
    trx_id,
    trx_state,
    trx_started,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS duration_seconds,
    trx_mysql_thread_id,
    trx_query
FROM information_schema.innodb_trx
WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) >= 30
ORDER BY trx_started ASC
```

---

# 6. Lock Wait 조회

## Request

```http
GET /api/v1/database-instances/{databaseId}/diagnostics/lock-waits
```

## Response

```json
[
  {
    "databaseId": 1,
    "engine": "MYSQL",
    "waitingTransactionId": "waiting-1",
    "waitingThreadId": 11,
    "waitingQueryPreview": "UPDATE orders SET status = 'PAID'",
    "blockingTransactionId": "blocking-1",
    "blockingThreadId": 10,
    "blockingQueryPreview": "SELECT * FROM orders WHERE id = 1"
  }
]
```

## 설명

Lock Wait 관계를 조회합니다.

사용 쿼리:

```sql
SELECT
    waiting_trx.trx_id AS waiting_trx_id,
    waiting_trx.trx_mysql_thread_id AS waiting_thread_id,
    waiting_trx.trx_query AS waiting_query,
    blocking_trx.trx_id AS blocking_trx_id,
    blocking_trx.trx_mysql_thread_id AS blocking_thread_id,
    blocking_trx.trx_query AS blocking_query
FROM information_schema.innodb_lock_waits lock_waits
JOIN information_schema.innodb_trx waiting_trx
    ON lock_waits.requesting_trx_id = waiting_trx.trx_id
JOIN information_schema.innodb_trx blocking_trx
    ON lock_waits.blocking_trx_id = blocking_trx.trx_id
```

Lock Wait이 없으면 빈 배열을 반환합니다.

```json
[]
```

---

# 7. Slow Query 후보 조회

## Request

```http
GET /api/v1/database-instances/{databaseId}/diagnostics/slow-queries
```

## Response

```json
[
  {
    "databaseId": 1,
    "engine": "MYSQL",
    "digestText": "SELECT * FROM orders WHERE id = ?",
    "executionCount": 100,
    "averageSeconds": 0.123456,
    "maxSeconds": 1.234567,
    "rowsExamined": 1000,
    "rowsSent": 10
  }
]
```

## 설명

Performance Schema의 SQL Digest 통계를 기준으로 느린 SQL 후보를 조회합니다.

사용 쿼리:

```sql
SELECT
    DIGEST_TEXT,
    COUNT_STAR,
    ROUND(AVG_TIMER_WAIT / 1000000000000, 6) AS avg_seconds,
    ROUND(MAX_TIMER_WAIT / 1000000000000, 6) AS max_seconds,
    SUM_ROWS_EXAMINED,
    SUM_ROWS_SENT
FROM performance_schema.events_statements_summary_by_digest
WHERE DIGEST_TEXT IS NOT NULL
ORDER BY AVG_TIMER_WAIT DESC
LIMIT 10
```

Performance Schema에 통계가 없으면 빈 배열을 반환할 수 있습니다.

---

## 현재 지원 API 요약

| 기능 | Method | Path |
|---|---|---|
| Version | GET | `/api/v1/database-instances/{databaseId}/diagnostics/version` |
| Uptime | GET | `/api/v1/database-instances/{databaseId}/diagnostics/uptime` |
| Connection Summary | GET | `/api/v1/database-instances/{databaseId}/diagnostics/connections` |
| Sessions | GET | `/api/v1/database-instances/{databaseId}/diagnostics/sessions` |
| Long Transactions | GET | `/api/v1/database-instances/{databaseId}/diagnostics/long-transactions` |
| Lock Waits | GET | `/api/v1/database-instances/{databaseId}/diagnostics/lock-waits` |
| Slow Queries | GET | `/api/v1/database-instances/{databaseId}/diagnostics/slow-queries` |

---

## 보안 기준

Diagnostics API는 다음 기준을 따릅니다.

- Credential은 응답에 포함하지 않음
- 임의 SQL 실행 API를 제공하지 않음
- SQL Preview는 길이를 제한함
- 등록된 DB 인스턴스만 진단 대상으로 사용함
- INACTIVE 상태의 DB는 진단하지 않음

---

## 현재 한계

현재 Diagnostics API는 실시간 조회 방식입니다.

아직 다음 기능은 포함하지 않았습니다.

- 진단 결과 저장
- 진단 Snapshot 비교
- Health Score 반영
- Alert 연동
- Capability API
- PostgreSQL Diagnostics