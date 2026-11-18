# DB FleetOps 금융·보험 환경 검증 로드맵

## 1. 목표

DB FleetOps를 단순한 DB 운영 도구에서 다음과 같은 방향으로 한 단계 확장한다.

> **금융·보험 환경을 가정해 데이터의 안정성·복구 가능성·운영 통제를 검증하는 데이터베이스 운영 플랫폼**

한화 금융 Software 직군에서 요구하는 다음 역량과 연결하는 것을 목표로 한다.

- 시스템·애플리케이션 아키텍처 설계
- Back-End / API 개발
- 서버·DB·배치 운영
- 장애 대응 및 품질 관리
- 데이터 분석 기반 제품 개선
- 금융·보험 도메인의 데이터 정합성 및 운영 안정성 이해

보험 서비스를 새로 만드는 것이 아니라 Insurance Sandbox를 복원 후 업무 정합성을 검증하는
대상 데이터로 사용한다. 포트폴리오의 주인공은 FleetOps이며, 보험 데이터는 FleetOps의 복원
검증 능력을 증명하는 시나리오다.

이 프로젝트는 실제 금융회사 시스템이나 규제 준수 제품이라고 주장하지 않는다. 금융·보험
환경의 업무지속성, 복구와 데이터 정합성 요구를 참고해 설계하고 실험한 프로젝트로 표현한다.

### 진행 원칙

- 새로운 개념은 구현 전에 `docs/용어 정리.md`에 표준 용어와 코드 이름을 먼저 정의한다.
- 책임과 불변식은 `docs/도메인 정리.md`에 먼저 반영한 뒤 코드로 옮긴다.
- 코드와 문서에서는 정리된 유비쿼터스 언어만 사용한다.
- 클래스와 메서드는 한 가지 책임을 드러내는 단순한 이름을 사용한다.
- `docs/diagram/유스케이스별-객체-관계.md`에는 FleetOps 런타임에 실제 존재하는 객체와
  유스케이스만 표현한다. Insurance Sandbox의 ER 구조는 포함하지 않는다.
- 문서에만 존재하는 Service, 클래스와 메서드를 다이어그램에 미리 만들지 않는다.

---

# 2. 전체 6주 로드맵

| 주차 | 목표 | 핵심 구현 | 포트폴리오에서 보여줄 것 |
|---|---|---|---|
| 1주차 | Insurance Sandbox 구축 | 정상 Seed, 오류 데이터, 반복 실행 도구 | 재현 가능한 검증 대상 DB |
| 2주차 | 업무 정합성 검증 | 기존 복원 검증에 업무 Rule 결과 통합 | 기술적 복원 성공 ≠ 업무 복구 성공 |
| 3주차 | 인증·RBAC + Audit 강화 | 사용자 식별, 위험 작업 권한, 기존 감사 기록 확장 | 운영 통제와 추적 가능성 |
| 4주차 | 장애·복구 실험 정교화 | Agent 종료·재시작, DB Down, 응답 유실, 복원 실패 | 장애 대응과 복구 가능성 |
| 5주차 | SLO / Observability 확장 | 기존 Metric에 실행 시간과 복구 시간 추가 및 실측 | 데이터 기반 운영 개선 |
| 6주차 | README·포트폴리오 재구성 | Architecture, 실험 조건·결과, 직무 연결 | 검증 가능한 최종 지원자료 |

---

# 3. 1주차 — Insurance Sandbox 구축

## 목적

보험 애플리케이션 자체를 만드는 것이 아니라, **DB FleetOps가 운영·검증할 대상 데이터베이스**를 만든다.

## 권장 테이블

```text
insurance_sandbox

customer
policy
premium_payment
claim
claim_payment
```

## 관계 구조

```text
Customer
   │
   └── Policy
         │
         ├── PremiumPayment
         │
         └── Claim
                │
                └── ClaimPayment
```

## 상태 예시

```text
PolicyStatus

APPLICATION
UNDERWRITING
ACTIVE
LAPSED
TERMINATED
MATURED
```

```text
ClaimStatus

RECEIVED
REVIEWING
APPROVED
REJECTED
PAID
```

## Seed Data 규모

```text
Customer        10,000
Policy          30,000
PremiumPayment 300,000
Claim           20,000
ClaimPayment    10,000
```

실제 개인정보는 사용하지 않고 Synthetic Data만 사용한다.

## 의도적인 오류 데이터

다음 오류 데이터를 정상 Seed와 분리해 생성한다.

- 같은 Policy·회차의 완료된 PremiumPayment 중복
- 같은 Claim의 완료된 ClaimPayment 중복
- 존재하지 않는 Policy를 참조하는 Claim
- 완료됐지만 지급금액이 0인 ClaimPayment
- 존재하지 않는 Customer를 참조하는 `ACTIVE` Policy

## 완료 기준

> 정상 Seed와 오류 데이터를 반복해서 구성하고, FleetOps가 해당 DB를 등록·점검·백업·복원
> 검증할 수 있는 상태

보험 서비스 자체 구현은 필요하지 않다.

## 1주차 구현 기준

- 기본 관리 대상 DB 이름은 `insurance_sandbox`로 사용한다.
- `Customer`, `Policy`, `PremiumPayment`, `Claim`, `ClaimPayment`는 관리 대상 MySQL의
  독립적인 데이터 모델이며 FK와 ID로 연결한다. FleetOps Java 도메인의 Aggregate로 다루지 않는다.
- 기본 Seed에는 정상 합성 데이터만 포함하고 오류 데이터는 `inject` 명령으로 분리한다.
- `verify`는 Schema, 전체 Seed 건수와 업무 불변식을 확인한다.
- `reset`은 예약된 오류 데이터만 제거하고 정상 상태를 다시 확인한다.
- 보험 서비스의 Java Entity, CRUD API와 화면은 1주차 범위에 포함하지 않는다.
- 기술적 복원 검증은 오류 데이터를 복원할 수 있어도 통과할 수 있다. 해당 오류의 업무
  정합성 판정은 2주차 업무 정합성 검증에서 담당한다.

---

# 4. 2주차 — 업무 정합성 검증

## 핵심 목표

현재 복원 검증은 다음 순서로 실행된다.

```text
논리 백업
→ 임시 DB 복원
→ Table 확인
→ Row Count 확인
→ 기술 검증 결과
```

이를 다음과 같이 확장한다.

```text
논리 백업
 ↓
임시 DB 복원
 ↓
기술 검증
 ↓
업무 정합성 검증
 ↓
최종 검증 결과
```

## 기존 복원 검증과의 통합

```text
BackupRestoreVerification
 ├── 기술 검증 결과
 │    ├── Table 존재 여부
 │    └── Row Count
 └── 업무 정합성 검증 결과
      └── Rule별 결과
```

별도의 복원 검증 생명주기를 만들지 않는다. 기존 `BackupRestoreVerification`이 전체 검증을
대표하고 기술 검증과 업무 정합성 검증 결과를 구분해 기록한다.

관리 DB마다 적용할 Rule 구성이 필요하면 Profile을 둘 수 있지만, Profile은 Rule 선택과 설정만
책임진다. 코드 이름은 구현 전에 `docs/용어 정리.md`와 `docs/도메인 정리.md`에서 먼저 확정한다.

## Rule 실행 경계

- Rule은 복원된 임시 DB에서만 실행한다.
- 2주차에는 마이그레이션이나 내부 설정으로 등록하고 공개 Rule CRUD API는 만들지 않는다.
- 코드 Catalog에서 검토된 내부 Rule만 사용하고, Agent는 읽기 전용 Transaction에서 단일
  `SELECT`를 실행한다.
- 실행 시간과 결과 행 수를 제한한다.
- SQL과 결과를 기록할 때 DB 접속 인증 정보와 개인정보를 포함하지 않는다.
- 범용 SQL 편집 화면은 이번 로드맵 범위에서 제외한다.

## 예시 Rule 1 — 보험료 중복 수납 검증

```sql
SELECT policy_id, installment_number, COUNT(*)
FROM premium_payment
WHERE status = 'COMPLETED'
GROUP BY policy_id, installment_number
HAVING COUNT(*) > 1;
```

정상 조건:

```text
0 rows
```

## 예시 Rule 2 — 동일 Claim 중복 지급 검증

```sql
SELECT claim_id, COUNT(*)
FROM claim_payment
WHERE status = 'COMPLETED'
GROUP BY claim_id
HAVING COUNT(*) > 1;
```

정상 조건:

```text
0 rows
```

## 업무 Rule

- 같은 Policy·회차의 완료된 PremiumPayment가 하나만 존재
- 같은 Claim의 완료된 ClaimPayment가 하나만 존재
- Claim은 존재하는 Policy를 참조
- 완료된 ClaimPayment의 지급금액은 0보다 큼
- `PAID` Claim에는 완료된 ClaimPayment가 존재
- `ACTIVE` Policy는 존재하는 Customer를 참조
- 완료된 ClaimPayment의 지급금액은 승인금액을 초과하지 않음

계약번호·청구번호 Unique, FK, 상태값과 기본 금액 범위는 Schema 검증에서 확인한다. 여러 행이나
상태를 함께 판단해야 하는 규칙은 업무 정합성 검증에서 확인한다.

## 반드시 만들 테스트

### 정상 DB

```text
임시 DB 복원
→ 기술 검증 VERIFIED
→ 업무 정합성 검증 VERIFIED
→ 최종 검증 VERIFIED
```

### 오류 DB

```text
완료된 ClaimPayment 중복 주입
        ↓
논리 백업
        ↓
임시 DB 복원
        ↓
기술 검증 VERIFIED
        ↓
업무 정합성 검증 FAILED
        ↓
Rule:
DUPLICATE_COMPLETED_CLAIM_PAYMENT
```

오류 데이터에서도 백업 산출물 생성과 기술 검증 성공 여부를 별도로 보존한다. 업무 정합성
검증이 실패하면 최종 `OperationJob`은 실패로 종료하되 어느 단계와 Rule에서 실패했는지 조회할
수 있어야 한다.

## 포트폴리오 핵심 메시지

> 복원이 기술적으로 성공하더라도 업무 데이터 정합성이 깨져 있으면 복구 성공으로 판단하지 않았다.

이 기능이 이번 한화 금융 지원에서 가장 중요한 차별화 포인트다.

## 완료 기준

- 정상 Seed는 기술 검증과 업무 정합성 검증을 모두 통과한다.
- 오류 데이터는 기술 검증을 통과하고 지정된 업무 Rule에서 실패한다.
- 검증 API에서 단계별 상태, 실패 Rule과 실패 이유를 조회할 수 있다.
- Rule의 시간 제한, 결과 제한과 허용되지 않은 SQL 거절을 자동 테스트한다.
- 기존 복원 검증 API와 백업 Workflow 테스트에 회귀가 없다.

---

# 5. 3주차 — 인증·RBAC + Audit 강화

## 현재 기준

FleetOps에는 `AuditLog`와 Agent Token 인증이 이미 있다. 3주차에는 사용자 API 인증을 추가하고,
현재 감사 기록을 인증된 사용자와 위험 작업의 변경 내용을 추적할 수 있도록 확장한다. 사용자
역할과 Agent Token의 책임을 섞지 않는다.

## 역할 정의

복잡하게 만들 필요 없이 다음 3단계로 구성한다.

```text
VIEWER
OPERATOR
ADMIN
```

## 권한 매핑 예시

| 작업 | Viewer | Operator | Admin |
|---|---:|---:|---:|
| DB 상태 조회 | O | O | O |
| Job 조회 | O | O | O |
| Backup 실행 | X | O | O |
| 복원 검증 | X | O | O |
| Config 변경 | X | X | O |
| Credential 관리 | X | X | O |

## 핵심 목표

단순 Spring Security 적용이 아니라 **위험 작업 통제**를 보여주는 것이 중요하다.

```text
POST /databases/{id}/config

OPERATOR
→ 403 Forbidden

ADMIN
→ Accepted
→ Audit Log
```

## AuditLog 확장 필드

```text
AuditLog

actor
actorRole
action
resourceType
resourceId
requestId
beforeValue
afterValue
reason
result
timestamp
```

## Config 변경 예시

```text
BEFORE
max_connections = 300

AFTER
max_connections = 500

ACTOR
admin01

REASON
INC-2026-001
```

## 포트폴리오에서 설명할 질문

> 누가 운영 DB를 변경했는지 어떻게 확인합니까?

답:

- 사용자 식별
- 역할 기반 권한
- 위험 작업 제한
- 변경 전/후 값 기록
- 요청 ID 및 사유 기록
- 인증 정보와 비밀 값 Masking
- Audit Log 보존

`actor`와 `actorRole`은 요청 Body가 아니라 인증된 사용자 정보에서 가져온다. DB 비밀번호,
Agent Token과 그 밖의 인증 정보는 변경 전·후 값과 메시지에 기록하지 않는다. 이 구현을 근거로
실제 금융 규제 준수나 금융권 수준의 보안을 달성했다고 표현하지 않는다.

## 완료 기준

- 인증되지 않은 사용자 요청은 `401 Unauthorized`로 거절한다.
- 인증됐지만 권한이 부족한 위험 작업은 `403 Forbidden`으로 거절한다.
- 허용된 위험 작업은 인증된 사용자, 요청 ID, 사유와 결과를 감사 기록으로 남긴다.
- 설정 변경 전·후 값에는 인증 정보와 비밀 값이 포함되지 않는다.
- 기존 Agent Token 인증과 내부 Task API 동작을 유지한다.

---

# 6. 4주차 — 장애·복구 실험 정교화

## 권장 장애 시나리오

| 장애 | 확인할 것 |
|---|---|
| Agent SIGKILL | 실행권 만료 후 Task가 재대기되는가 |
| Agent 재시작 | 같은 Agent가 재대기된 Task를 다시 선점하는가 |
| DB Down | Task가 실패로 종료되고 이유가 기록되는가 |
| Network Delay | 장시간 지연에도 Control Plane이 정상인가 |
| Response Loss | 재시도 시 Job이 중복 생성되지 않는가 |
| Restore Failure | 검증 실패가 Success로 기록되지 않는가 |

## 핵심 실험 1 — 응답 유실 + 재시도

```text
Client
  │
  │ POST Backup
  ▼
Control Plane
  │
 Job Created
  │
  X Response Lost

Client Timeout
  │
  │ Retry with same Idempotency-Key
  ▼
Control Plane
  │
Existing Job 반환
```

검증값:

```text
Job count = 1
Task count = 1
Result duplication = 0
```

## 핵심 실험 2 — Agent 장애

```text
Task RUNNING
     │
Agent SIGKILL
     │
Lease Expire
     │
Task 재대기
     │
같은 Agent 재시작
     │
Task 재선점·재실행
     │
SUCCESS
```

현재 `OperationTask`는 생성 시 지정된 Agent에 계속 배정된다. 다른 Agent가 Task를 회수하는
시나리오는 Agent 재배정, DB 접근 권한과 이전 실행 차단 정책이 필요하므로 이번 범위에서 제외한다.
실험에서는 반드시 실제 복구 시간을 측정한다.

## 완료 기준

- 장애 시나리오는 자동화하여 같은 조건으로 반복 실행할 수 있다.
- Agent 종료 후 Task가 유실되지 않고 실행권 만료 뒤 재대기된다.
- 같은 Agent 재시작 후 Task를 다시 선점하며 이전 실행 번호의 결과 보고는 거절된다.
- 응답 유실 후 같은 Idempotency Key로 재요청해도 Job과 Task가 중복 생성되지 않는다.
- 장애 종류, 주입 시각, 감지 시각, 복구 시각과 최종 상태를 결과 문서에 기록한다.

---

# 7. 5주차 — SLO / Observability

## 현재 기준과 핵심 목표

FleetOps에는 Prometheus, Grafana와 Job·Task·Agent·복원 검증 상태별 Gauge가 이미 있다. 5주차는
관측성 도구를 새로 붙이는 작업이 아니라 실행 시간과 복구 성능을 측정할 수 있도록 기존 Metric을
확장하는 작업이다.

> 어떤 지표를 보고, 무엇을 판단했으며, 어떤 개선을 했는가

## 권장 SLI

```text
API Latency
Job Success Rate
Job Queue Time
Task Acquisition Delay
Task Execution Duration
Task Recovery Time
Backup Duration
Restore Verification Duration
Restore Verification Success Rate
Agent Heartbeat Delay
```

## 권장 목표값 예시

```text
같은 부하 조건에서 Control Plane API P95 목표

Agent 장애 감지 시간 목표

만료된 Task 재선점 시간 목표

복원 검증 완료율 목표
```

먼저 기준값을 측정한 뒤 목표값을 정한다. API 응답 시간은 요청량, 데이터 크기와 실행 환경을
함께 기록하며 실제 측정값만 사용한다. `Duplicate Job = 0`과 복원 검증 오탐 방지는 SLO가 아니라
항상 지켜야 하는 정확성 불변식으로 자동 테스트한다.

## Dashboard 구성

```text
System
- API latency
- 5xx rate

Job
- queued
- running
- failed
- retry

Agent
- active
- stale
- heartbeat lag

Backup / Restore
- success rate
- duration
- verification failure
```

## 데이터 기반 개선 예시

### 가설

```text
Agent polling 간격이 길어지면
Task 선점 지연이 증가할 것이다.
```

### 측정

```text
Agent polling interval = 5 sec
P95 선점 지연 = 실제 측정값
```

### 개선

```text
Agent polling interval = 1 sec
```

### 결과

```text
P95 선점 지연
Before → After
```

실제 측정한 값만 포트폴리오에 사용한다.

## 완료 기준

- Job 대기 시간, Task 선점 지연·실행 시간·복구 시간과 백업·복원 검증 시간을 수집한다.
- 정상 실행과 장애 실험을 같은 환경에서 반복 측정한다.
- Dashboard에서 상태 수뿐 아니라 지연과 처리 시간 분포를 확인할 수 있다.
- 부하 조건과 기준값을 기록한 뒤 SLO 후보와 근거를 정한다.
- 개선 전·후 값을 같은 측정 조건으로 비교한다.

## 직무 연결

이 단계는 공고의 다음 항목과 직접 연결된다.

- 장애 대응 및 품질 관리
- 데이터 분석 기반 제품 개선 가설 수립 및 검증

---

# 8. 6주차 — GitHub README 및 포트폴리오 재구성

## README 첫 문장 개선

기존의 단순한 소개보다 다음과 같이 프로젝트의 문제의식을 먼저 보여준다.

> **DB FleetOps는 장시간 DB 운영 작업의 실패·재시도·중복 요청을 안전하게 처리하고, 실제 복원 및 업무 데이터 정합성까지 검증하는 데이터베이스 운영 플랫폼입니다.**

## README 상단 핵심 문제

```text
1. 긴 DB 작업을 HTTP 요청과 분리
2. Agent 장애에도 실행권 회수
3. 동일 요청 재전송에도 중복 실행 방지
4. 논리 백업이 실제 업무 복구 가능한지 검증
```

이후 Architecture와 실제 장애 테스트 결과를 배치한다.

---

# 9. 최종 포트폴리오 7장 구성

## 1장 — Problem

제목 예시:

> **DB FleetOps — 실패를 전제로 설계한 데이터베이스 운영 플랫폼**

핵심 메시지:

> 운영 DB 작업은 실패할 수 있고, 재시도될 수 있으며, 결과를 증명할 수 있어야 한다.

금융 연결:

> 특히 계약·수납·지급과 같이 정합성이 중요한 금융 데이터에서는 “백업 파일 존재”가 아니라 “실제 복구 가능성”을 확인해야 한다고 판단했다.

---

## 2장 — Architecture

```text
                Operator
                   │
                   ▼
            ┌─────────────┐
            │ Control     │
            │ Plane       │
            └──────┬──────┘
                   │
             OperationJob
                   │
                 Worker
                   │
             OperationTask
                   │
                   ▼
              ┌─────────┐
              │ Go Agent│
              └────┬────┘
                   │
                   ▼
                 MySQL
```

설명:

- Control Plane: 상태와 정책 관리
- Worker: 실행 순서 조정
- Agent: DB 가까이에서 실제 작업 실행

---

## 3장 — Reliable Execution

핵심 내용:

- Job / Task 분리
- 비동기 처리
- Lease
- Retry
- Idempotency

메시지:

> 긴 DB 작업을 사용자 HTTP 요청과 분리하고, 실패와 재시도를 시스템 설계에 포함했다.

---

## 4장 — Failure Recovery

```text
Agent A
Task 선점
   │
   ▼
RUNNING
   │
 SIGKILL
   X
   │
Lease Expired
   │
   ▼
Task 재대기
   │
   ▼
Agent A 재시작
   │
   ▼
재선점
   │
   ▼
재실행
```

실측 결과:

```text
Task loss        0
Duplicate result 0
Recovery time    XX sec
```

XX에는 실제 측정값을 사용한다.

---

## 5장 — Financial Data Recovery

제목 예시:

> **백업은 언제 성공인가?**

Before:

```text
Dump 생성
  ↓
SUCCESS
```

After:

```text
Dump
 ↓
Restore
 ↓
Schema Validation
 ↓
Row Validation
 ↓
Business Invariant Validation
 ↓
SUCCESS
```

Insurance Sandbox 검증 예시:

```text
✓ policy_number uniqueness
✓ completed PremiumPayment duplicate = 0
✓ completed ClaimPayment duplicate = 0
✓ Claim without Policy = 0
✓ ACTIVE Policy without Customer = 0
✓ paidAmount exceeds approvedAmount = 0
```

핵심 메시지:

> 기술적 복구뿐 아니라 업무 데이터 정합성까지 확인해야 실제 복구가 완료된 것으로 판단했다.

---

## 6장 — Data-driven Improvement

표현할 내용:

- Prometheus / Grafana
- API P95 latency
- Task 선점 지연
- Recovery Time
- Backup Success Rate
- 복원 검증 실패
- Agent Heartbeat Delay

구성:

```text
가설
 ↓
측정
 ↓
장애 주입
 ↓
문제 확인
 ↓
개선
 ↓
Before / After
```

---

## 7장 — 한화 금융 Software 직무 연결

| 한화 Software 업무 | DB FleetOps |
|---|---|
| 시스템 아키텍처 | Control Plane / Worker / Agent 분리 |
| Back-End / API | Spring Boot REST |
| Server / DB | MySQL 운영 |
| 배치·장시간 작업 | Job / Task 비동기 처리 |
| 장애 대응 | Lease / Retry / Failure Injection |
| 품질 관리 | 복원 검증과 업무 정합성 검증 |
| 데이터 분석 | Prometheus / Grafana |
| 금융 데이터 안정성 | 업무 정합성 검증 |
| 보안·이력 | RBAC / Audit |

최종 메시지:

> 기능 구현에 그치지 않고 금융·보험 환경을 가정한 데이터를 안전하게 운영하고, 장애 발생 이후에도 복구 가능성을 검증하는 Backend/Platform Engineering 경험을 만들었다.

---

# 10. 개발 우선순위

시간이 부족하다면 다음 5개를 먼저 완성한다.

## 1순위 — Insurance Sandbox

- 보험 계약
- 보험료 수납
- 보험금 청구
- 보험금 지급

운영·검증용 테스트 DB로만 구현한다.

## 2순위 — 업무 정합성 검증

가장 중요하다.

기존 `BackupRestoreVerification`을 보험 업무 정합성까지 확장한다.

## 3순위 — 인증·RBAC + Audit 강화

사용자 인증과 위험 작업 권한을 추가하고 기존 감사 기록을 강화해 운영 통제와 추적 가능성을
보여준다.

## 4순위 — 장애 실험

- Agent SIGKILL
- Network Delay
- Response Loss
- DB Down
- Restore Failure

## 5순위 — Metric 확장 + SLO 실측

실제 측정 결과를 포트폴리오에 넣는다.

---

# 11. 후순위 기능

현재 단계에서는 다음 항목의 우선순위를 낮춘다.

- 대규모 React Front-End 개발
- iOS / Android 앱
- Kafka 추가
- MSA 구조로 무리하게 분리
- 보험료 계산 엔진
- 보험 상품 추천 AI
- Kubernetes 고도화
- 과도한 신규 기술 스택 추가

기술 개수를 늘리는 것보다 **기존 설계의 깊이와 금융 도메인 연결성**을 강화하는 것이 중요하다.

---

# 12. 권장 개발 순서

```text
Insurance Sandbox Schema
      ↓
정상 Seed / 오류 데이터
      ↓
업무 정합성 검증 용어·경계 확정
      ↓
기존 복원 검증 통합
      ↓
업무 Rule 실행·결과 기록
      ↓
사용자 인증 / RBAC
      ↓
Audit 강화
      ↓
장애·복구 실험
      ↓
Metric 확장 / 실측
      ↓
README / 포트폴리오
```

---

# 13. 최종 프로젝트 포지셔닝

DB FleetOps를 DBA 프로젝트로만 보이게 만들지 않는다.

또한 실제 보험 애플리케이션 전체를 만든 것처럼 과장하지 않는다.

가장 적합한 포지셔닝은 다음과 같다.

> **Backend/Platform Engineer가 금융·보험 환경을 가정해 데이터 운영 시스템의 안정성·정합성·복구 가능성을 설계하고 검증한 프로젝트**

또는 README/포트폴리오 소개 문구로는 다음 표현이 적합하다.

> **금융·보험처럼 데이터 정합성과 복구 가능성이 중요한 환경을 가정하여, DB 운영 작업을 안전하게 실행·추적하고 실제 복원 및 업무 데이터 정합성까지 검증하는 데이터베이스 운영 플랫폼**

---

# 14. 핵심 면접 메시지

면접에서 “보험 서비스와 DB FleetOps가 어떤 관계가 있는가?”라는 질문을 받으면 다음 논리로 답한다.

> 고객 채널의 기능만큼 그 뒤의 계약·수납·지급 데이터가 안정적으로 유지되는 것도 중요하다고 생각했습니다. DB FleetOps는 보험 애플리케이션 자체를 구현한 프로젝트는 아니지만, 장시간 DB 작업의 비동기 처리, 중복 요청 방지, 장애 시 작업 실행권 회수, 변경 이력, 실제 복원 검증 등을 구현했습니다. 이후 보험 도메인을 조사하면서 복원 성공 기준을 단순 Row Count에서 계약·수납·지급 데이터의 업무 정합성 검증까지 확장했습니다.

---

# 15. 가장 중요한 한 문장

> **“금융 도메인을 공부했다”가 아니라, “금융 도메인의 특성을 기존 시스템 설계에 반영했다”는 것을 코드와 실험으로 보여준다.**

---

# 16. 방향성 검증 근거

- 한화생명 IT 직무는 IT 전문성뿐 아니라 보험업 본질과 전사 업무에 대한 이해, 시스템·보안
  Architecture 설계와 문제 해결 역량을 함께 요구한다.
  - <https://www.hanwhain.com/web/meet/detail_job/view.do?djSeq=234&pg=4&pgSz=20&sdSeq=201>
- 전자금융감독규정 제23조는 업무지속성 확보방안에 백업·재해복구 계획과 모의훈련을 포함하고,
  그 실효성과 적정성을 정기적으로 점검하도록 규정한다.
  - <https://www.law.go.kr/LSW/admRulSideInfoP.do?admRulSeq=2100000274812&chrClsCd=010201&dashNo=&docCls=jo&joBrNo=00&joNo=0023&urlMode=admRulScJoRltInfoR>

위 근거는 프로젝트 방향을 설명하기 위한 참고 자료다. FleetOps가 실제 금융 규제 준수 여부를
검증받았다는 의미로 사용하지 않는다.
