# DB FleetOps 아키텍처

## 개요

DB FleetOps는 여러 DB 인스턴스를 등록하고, 상태 점검과 운영 진단을 수행하기 위한 DB 운영관리 플랫폼입니다.

현재는 Modular Monolith 구조를 기반으로 구현하고 있으며, Health 모듈 내부에는 Port & Adapter 구조를 적용했습니다.

주요 목적은 다음과 같습니다.

- Inventory와 Health 책임 분리
- DBMS별 구현 차이 격리
- MySQL 이후 PostgreSQL 확장 가능성 확보
- 외부 DB 접근 로직을 application 계층에서 분리

---

## 현재 디렉토리 구조

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

## 계층별 역할

| 계층 | 역할 |
|---|---|
| api | HTTP 요청과 응답 처리 |
| application | Use Case 실행, 상태 검증, Port 선택 |
| domain | 비즈니스 값과 상태 표현 |
| port | 외부 시스템 접근을 위한 인터페이스 |
| infra | Port의 실제 기술 구현체 |
| dto | API 응답 모델 |

---

## 모듈별 책임

### database

관리 대상 DB 인스턴스를 관리합니다.

담당 기능은 다음과 같습니다.

- DB 등록
- DB 목록 조회
- DB 상세 조회
- DB 수정
- DB 비활성화
- Credential 관리

Health Check나 Diagnostics 로직은 포함하지 않습니다.

### health

등록된 DB의 상태 점검과 운영 진단을 담당합니다.

담당 기능은 다음과 같습니다.

- 기본 DB Health Check
- Inventory 기반 Health Check
- Health Result 저장
- MySQL 운영 진단
- DBMS별 Port 선택
- MySQL Adapter 실행

### common

공통 기능을 담당합니다.

현재 포함된 내용은 다음과 같습니다.

- Global Exception
- Problem Details
- RequestId Filter
- Logging
- Configuration

---

## Port & Adapter 기준

명명 기준은 다음과 같이 고정합니다.

```text
Port
→ application이 필요로 하는 외부 기능 인터페이스

Adapter
→ Port를 실제 기술로 구현한 클래스

Registry
→ 여러 Port 구현체 중 적절한 구현체를 선택하는 application 컴포넌트
```

현재 Health 모듈의 Port 구조는 다음과 같습니다.

| Port | 구현체 | 역할 |
|---|---|---|
| DatabaseHealthProbe | 기존 기본 DB Probe 구현체 | Phase 1 기본 DB 점검 |
| DatabaseHealthCheckPort | MySqlHealthAdapter | Inventory 기반 Health Check |
| DatabaseDiagnosticPort | MySqlDiagnosticAdapter | MySQL 운영 진단 |

---

## 기본 DB Health Check 흐름

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

이 기능은 `db-fleetops.target` 설정을 기준으로 동작합니다.

---

## Inventory 기반 Health Check 흐름

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
DatabaseEngine 기준 Health Check Port 선택
  ↓
MySQL Adapter 실행
  ↓
Health Result 저장
  ↓
응답 반환
```

---

## MySQL Diagnostics 흐름

```text
GET /api/v1/database-instances/{databaseId}/diagnostics/*
        │
        ▼
DatabaseDiagnosticController
        │
        ▼
DatabaseDiagnosticService
        │
        ▼
ManagedDatabaseRepository
        │
        ▼
DatabaseCredentialRepository
        │
        ▼
DatabaseDiagnosticPortRegistry
        │
        ▼
DatabaseDiagnosticPort
        │
        ▼
MySqlDiagnosticAdapter
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
DatabaseEngine 기준 Diagnostic Port 선택
  ↓
MySQL 진단 SQL 실행
  ↓
응답 DTO 변환
  ↓
응답 반환
```

현재 Diagnostics는 실시간 조회 방식입니다.  
진단 결과 저장은 이후 Phase에서 별도로 검토합니다.

---

## MySQL Diagnostics 구현 범위

현재 구현된 진단 항목은 다음과 같습니다.

| 기능 | API |
|---|---|
| Version | `/diagnostics/version` |
| Uptime | `/diagnostics/uptime` |
| Connection Summary | `/diagnostics/connections` |
| Sessions | `/diagnostics/sessions` |
| Long Transactions | `/diagnostics/long-transactions` |
| Lock Waits | `/diagnostics/lock-waits` |
| Slow Queries | `/diagnostics/slow-queries` |

임의 SQL 실행 API는 제공하지 않습니다.

---

## Credential 분리

DB 메타데이터와 접속정보를 분리했습니다.

```text
ManagedDatabase
      │
      └──────────► DatabaseCredential
```

분리한 이유는 다음과 같습니다.

- 응답 DTO에 비밀번호가 노출되지 않음
- Credential 교체가 쉬움
- 향후 Secret Manager 연동 가능
- Credential 접근 권한을 별도로 분리할 수 있음

현재는 단순 Entity 분리 단계입니다.

---

## Metadata DB와 Target DB 구분

현재 설정은 두 종류의 DB를 사용합니다.

| 설정 | 역할 |
|---|---|
| spring.datasource | Inventory, Credential, Health Result 저장 |
| db-fleetops.target | Phase 1 기본 Health Check 대상 |

Phase 2 이후 주요 기능은 등록된 DB 인스턴스를 기준으로 동작합니다.

---

## 현재 구현 범위

### 완료

- 기본 DB Health Check
- Inventory CRUD
- Credential 분리
- Inventory 기반 Health Check
- Health Check Port 구조
- MySQL Health Adapter
- Health Result 저장
- Diagnostic Port 구조
- MySQL Diagnostic Adapter
- MySQL Version 진단
- MySQL Uptime 진단
- MySQL Connection 진단
- MySQL Session 진단
- MySQL Long Transaction 진단
- MySQL Lock Wait 진단
- MySQL Slow Query 진단
- Service 단위 테스트
- Controller MVC 테스트
- Port Registry 테스트
- Integration Test 분리

### 미구현

- Diagnostic Snapshot 저장
- Capability 조회
- Audit
- Operation Job
- Agent
- Drift
- RBAC

---

## 테스트 구조

```text
src/test
→ 단위 테스트, MVC 테스트

src/integrationTest
→ 실제 MySQL 연결 테스트
```

테스트 책임은 다음과 같습니다.

| 테스트 | 검증 내용 |
|---|---|
| Service Test | Use Case 흐름, Port 선택, DTO 변환 |
| Controller Test | URL, HTTP Status, JSON 응답 |
| Port Registry Test | Engine별 Port 선택 |
| Integration Test | 실제 MySQL 연결 및 SQL 실행 |

일반 테스트는 외부 MySQL에 의존하지 않도록 구성합니다.

---

## 다음 단계

다음 단계에서는 Operation Job 또는 Diagnostic Snapshot 저장 구조를 검토합니다.

우선순위 후보는 다음과 같습니다.

1. Diagnostics API 문서 보강
2. Health Check 이력 조회 API
3. Diagnostic Snapshot 저장
4. Operation Job 설계
5. Audit Log 설계