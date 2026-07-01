# DB FleetOps 아키텍처

## 개요

DB FleetOps는 여러 DB 인스턴스를 하나의 플랫폼에서 등록하고 관리하기 위한 운영 플랫폼입니다.

현재는 Modular Monolith 구조를 기반으로 구현하고 있으며, 각 모듈 내부에서는 Port & Adapter 구조를 일부 적용하고 있습니다.

주요 목적은 다음과 같습니다.

- Inventory와 Health 책임 분리
- DBMS별 구현 차이 격리
- MySQL 이후 PostgreSQL 확장 가능성 확보
- 외부 DB 접근 로직을 application 계층에서 분리

---

# 현재 디렉토리 구조

```text
src/main/java/com/dbfleetops

├── common
│   ├── config
│   ├── exception
│   ├── logging
│   └── web
│
├── database
│   ├── api
│   ├── application
│   ├── domain
│   ├── dto
│   └── infra
│
├── health
│   ├── api
│   ├── application
│   ├── domain
│   ├── dto
│   ├── infra
│   └── port
│
└── DbFleetopsApplication.java
```

---

# 계층과 역할

## api

외부 HTTP 요청을 받는 계층입니다.

담당 책임은 다음과 같습니다.

- URL 매핑
- Request Body 수신
- PathVariable 처리
- Response 반환

비즈니스 판단은 하지 않습니다.

---

## application

Use Case를 실행하는 계층입니다.

담당 책임은 다음과 같습니다.

- 도메인 조회
- 상태 검증
- Port 선택
- 트랜잭션 경계 설정
- 결과 저장
- DTO 변환 흐름 제어

외부 DB에 직접 접속하지 않습니다.

---

## domain

비즈니스 상태와 값을 표현합니다.

예시는 다음과 같습니다.

- ManagedDatabase
- DatabaseCredential
- DatabaseHealth
- DatabaseHealthResult
- HealthStatus

HTTP, JDBC, Spring 기술에 직접 의존하지 않도록 유지합니다.

---

## port

application 계층이 외부 시스템을 사용하기 위해 정의한 인터페이스입니다.

현재 Health 모듈에는 다음 Port가 있습니다.

```text
DatabaseHealthProbe
DatabaseHealthCheckPort
```

각 역할은 다음과 같습니다.

| Port | 역할 |
|---|---|
| DatabaseHealthProbe | Phase 1 기본 DB 점검용 Port |
| DatabaseHealthCheckPort | Phase 2 Inventory 기반 DB 점검용 Port |

---

## infra

Port의 실제 구현체가 위치합니다.

현재 구현체는 다음과 같습니다.

```text
MySqlHealthAdapter
```

이 구현체는 MySQL JDBC 연결, `SELECT 1` 실행, 응답시간 측정을 담당합니다.

---

# 모듈별 책임

## database

관리 대상 DB 자체를 관리합니다.

담당 기능은 다음과 같습니다.

- DB 등록
- DB 목록 조회
- DB 상세 조회
- DB 수정
- DB 비활성화
- Credential 관리

Health Check 로직은 포함하지 않습니다.

---

## health

등록된 DB의 상태를 점검합니다.

담당 기능은 다음과 같습니다.

- 기본 DB Health Check
- Inventory 기반 Health Check
- DBMS별 Health Check Port 선택
- Health 상태 계산
- Health Result 저장

Inventory CRUD 기능은 포함하지 않습니다.

---

## common

공통 기능을 제공합니다.

현재 포함된 내용은 다음과 같습니다.

- Global Exception
- Problem Details
- RequestId Filter
- Logging
- Configuration

---

# 현재 아키텍처 흐름

## 1. 기본 DB Health Check

Phase 1에서 구현한 흐름입니다.

```text
GET /api/v1/databases/default/health
        │
        ▼
DatabaseHealthController
        │
        ▼
DatabaseHealthService
        │
        ▼
DatabaseHealthProbe
        │
        ▼
기본 설정 DB 연결 점검
```

이 기능은 `application.yml` 또는 환경변수에 설정된 기본 DB를 점검합니다.

---

## 2. Inventory 기반 Health Check

Phase 2에서 추가된 흐름입니다.

```text
POST /api/v1/database-instances/{databaseId}/health-checks
        │
        ▼
DatabaseHealthController
        │
        ▼
DatabaseHealthService
        │
        ▼
ManagedDatabaseRepository
        │
        ▼
DatabaseCredentialRepository
        │
        ▼
DatabaseHealthCheckPortRegistry
        │
        ▼
DatabaseHealthCheckPort
        │
        ▼
MySqlHealthAdapter
        │
        ▼
DatabaseHealthResultRepository
```

처리 순서는 다음과 같습니다.

```text
databaseId 수신
  ↓
ManagedDatabase 조회
  ↓
ACTIVE 상태 확인
  ↓
Credential 조회
  ↓
DatabaseEngine 기준 Port 선택
  ↓
MySQL Adapter 실행
  ↓
Health Result 저장
  ↓
응답 반환
```

---

# Port & Adapter 구조

DBMS별 Health Check 방식은 서로 달라질 수 있습니다.

따라서 application 계층은 MySQL 구현체에 직접 의존하지 않고 Port에만 의존합니다.

```text
DatabaseHealthService
        │
        ▼
DatabaseHealthCheckPortRegistry
        │
        ▼
DatabaseHealthCheckPort
        │
        ▼
MySqlHealthAdapter
```

역할은 다음과 같습니다.

| 구성요소 | 위치 | 역할 |
|---|---|---|
| DatabaseHealthService | application | Health Check Use Case 실행 |
| DatabaseHealthCheckPortRegistry | application | DBMS에 맞는 Port 구현체 선택 |
| DatabaseHealthCheckPort | port | Inventory 기반 Health Check 인터페이스 |
| MySqlHealthAdapter | infra | MySQL Health Check 실제 구현 |

---

# Port와 Adapter 명명 기준

앞으로의 명명 기준은 다음과 같이 고정합니다.

```text
Port
→ application이 필요로 하는 외부 기능 인터페이스

Adapter
→ Port를 실제 기술로 구현한 클래스

Registry
→ 여러 Port 구현체 중 적절한 구현체를 선택하는 application 컴포넌트
```

예시는 다음과 같습니다.

```text
DatabaseHealthCheckPort
MySqlHealthAdapter
DatabaseHealthCheckPortRegistry
```

Phase 3 진단 기능도 같은 기준을 적용합니다.

```text
DatabaseDiagnosticPort
MySqlDiagnosticAdapter
DatabaseDiagnosticPortRegistry
```

---

# Credential 분리

DB 메타데이터와 접속정보를 분리했습니다.

```text
ManagedDatabase
      │
      └──────────► DatabaseCredential
```

분리한 이유는 다음과 같습니다.

- 응답 DTO에 비밀번호가 노출되지 않음
- 향후 Secret Manager 연동 가능
- Credential 교체가 쉬움
- Credential 접근 권한을 별도로 분리할 수 있음

현재는 단순 Entity 분리 단계입니다.

향후 개선 방향은 다음과 같습니다.

- 비밀번호 암호화
- Credential Reference 방식 적용
- Kubernetes Secret 연동
- Vault 연동
- Credential Rotation

---

# Metadata DB와 Target DB 구분

현재 설정은 두 종류의 DB를 사용합니다.

```text
spring.datasource
→ DB FleetOps Metadata DB

db-fleetops.target
→ Phase 1 기본 Health Check 대상 DB
```

역할은 다음과 같습니다.

| 설정 | 역할 |
|---|---|
| spring.datasource | Inventory, Credential, Health Result 저장 |
| db-fleetops.target | 기본 DB Health Check 대상 |

Phase 2부터는 등록된 DB 인스턴스를 기준으로 Health Check를 수행합니다.

---

# 현재 구현 범위

## 완료

- 기본 DB Health Check
- Inventory CRUD
- Credential 분리
- Inventory 기반 Health Check
- Health Check Port 구조
- MySQL Health Adapter
- Health Result 저장
- Service 단위 테스트
- Controller MVC 테스트
- Integration Test 분리

## 미구현

- MySQL 운영 진단
- Diagnostic Port
- Diagnostic Adapter
- Capability
- Audit
- Operation Job
- Agent
- Drift
- RBAC

---

# 테스트 구조

현재 테스트는 다음 기준으로 분리합니다.

```text
src/test
→ 단위 테스트, MVC 테스트

src/integrationTest
→ 실제 MySQL 연결 테스트
```

테스트 책임은 다음과 같습니다.

| 테스트 | 검증 내용 |
|---|---|
| Service Test | Use Case 흐름, Port 선택, 결과 저장 |
| Controller Test | URL, HTTP Status, JSON 응답 |
| Port Registry Test | Engine별 구현체 선택 |
| Integration Test | 실제 MySQL 연결 |

일반 테스트는 외부 MySQL에 의존하지 않도록 구성합니다.

---

# 다음 단계

Phase 3에서는 MySQL 운영 진단 기능을 추가합니다.

예정 구조는 다음과 같습니다.

```text
health
├── application
│   ├── DatabaseDiagnosticService.java
│   └── DatabaseDiagnosticPortRegistry.java
│
├── domain
│   ├── DatabaseVersionInfo.java
│   ├── DatabaseUptimeInfo.java
│   ├── ConnectionSummary.java
│   ├── SessionInfo.java
│   ├── LongTransactionInfo.java
│   ├── LockWaitInfo.java
│   └── SlowQueryInfo.java
│
├── port
│   └── DatabaseDiagnosticPort.java
│
└── infra
    └── MySqlDiagnosticAdapter.java
```

Phase 3 예정 기능은 다음과 같습니다.

- Version
- Uptime
- Connection 수
- Connection 사용률
- Session 목록
- Long Transaction
- Lock Wait
- Slow Query 후보

Phase 3에서도 임의 SQL 실행 API는 제공하지 않습니다.

진단에 필요한 정해진 조회 API만 제공합니다.