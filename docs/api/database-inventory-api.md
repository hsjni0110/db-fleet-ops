# Database Inventory API

## 개요

Inventory API는 DB FleetOps가 관리할 DB 인스턴스를 등록하고 조회하기 위한 API입니다.

현재 지원 DBMS

- MySQL

---

# 1. DB 등록

## Request

```http
POST /api/v1/database-instances
```

### Request Body

```json
{
  "name": "order-mysql",
  "host": "localhost",
  "port": 3306,
  "databaseName": "orders",
  "engine": "MYSQL",
  "environment": "LOCAL",
  "serviceName": "order-service",
  "owner": "platform-team",
  "description": "local mysql",
  "username": "root",
  "password": "password"
}
```

---

### Response

```json
{
  "id": 1,
  "name": "order-mysql",
  "host": "localhost",
  "port": 3306,
  "databaseName": "orders",
  "engine": "MYSQL",
  "status": "ACTIVE",
  "environment": "LOCAL",
  "serviceName": "order-service",
  "owner": "platform-team",
  "description": "local mysql"
}
```

---

# 2. DB 목록 조회

```http
GET /api/v1/database-instances
```

---

# Response

```json
[
  {
    "id": 1,
    "name": "order-mysql",
    "engine": "MYSQL",
    "status": "ACTIVE"
  }
]
```

---

# 3. DB 상세 조회

```http
GET /api/v1/database-instances/{databaseId}
```

---

# 4. DB 수정

```http
PATCH /api/v1/database-instances/{databaseId}
```

Request Body는 등록 API와 동일합니다.

---

# 5. DB 비활성화

```http
DELETE /api/v1/database-instances/{databaseId}
```

삭제 대신 상태를 `INACTIVE`로 변경합니다.

---

# 6. 기본 DB Health Check

```http
GET /api/v1/databases/default/health
```

기존 Phase 1에서 구현한 API입니다.

application.yml에 등록된 기본 DB를 점검합니다.

---

# Response

```json
{
  "databaseType": "MYSQL",
  "status": "UP",
  "latencyMs": 14
}
```

---

# 7. Inventory Health Check

```http
POST /api/v1/database-instances/{databaseId}/health-checks
```

등록된 DB를 대상으로 Health Check를 수행합니다.

---

## 처리 과정

```text
databaseId
      │
      ▼
ManagedDatabase 조회
      │
      ▼
Credential 조회
      │
      ▼
Adapter 선택
      │
      ▼
Health Check
      │
      ▼
Health Result 저장
```

---

## Response

```json
{
  "databaseId": 1,
  "status": "HEALTHY",
  "connectionSuccess": true,
  "responseTimeMs": 12,
  "message": "MySQL connection check succeeded."
}
```