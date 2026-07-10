# DB FleetOps

DB FleetOps는 여러 데이터베이스를 한 곳에서 관리하기 위한 DBMS 운영 관리 플랫폼입니다.

단순히 DB 연결 상태만 확인하는 도구가 아니라, 운영자가 실제로 마주치는 DB 운영 작업을 안전하게 처리하는 것을 목표로 합니다. 현재 프로젝트는 MySQL을 기준으로 Health Check, 비동기 Job 처리, Agent 기반 작업 실행, 백업, 복원 검증, Configuration Drift 탐지, Safe Configuration Apply, 배포 및 관측성 구성을 구현하고 있습니다.

---

## 1. 프로젝트 목적

DB 운영에서는 단순히 "DB가 켜져 있는가"만으로는 충분하지 않습니다.

운영 환경에서는 다음과 같은 질문에 답할 수 있어야 합니다.

```text
DB에 정상적으로 연결되는가?
장애 원인은 인증 실패인가, 네트워크 문제인가, 연결 거부인가?
백업 작업이 중복 실행되지 않는가?
백업 파일은 실제로 복원 가능한가?
DB 설정값이 운영 기준과 달라지지 않았는가?
설정을 변경할 때 변경 전후 값과 실행 결과가 남는가?
Agent가 정상적으로 살아 있고 작업을 가져가고 있는가?
Worker가 작업을 안전하게 처리하고 있는가?
배포 후 상태와 메트릭을 관측할 수 있는가?
```

DB FleetOps는 이러한 운영 질문을 API, Job Engine, Agent, Worker, Audit, Metrics 구조로 풀어내는 프로젝트입니다.

---

## 2. 현재 구현 범위

현재 구현된 주요 범위는 다음과 같습니다.

```text
Spring Boot 기반 Control Plane
MySQL Health Check
공통 오류 응답 및 Request ID 추적
단위 테스트 / 통합 테스트 분리
Operation Job Engine
Worker Claim / Lease / Retry / Audit 구조
Go Agent 기반 Host 작업 실행
OperationJob과 OperationTask 연결
MySQL Logical Backup 실행
백업 파일 검증
복원 검증 기반 백업 성공 판단
Configuration Drift 탐지
Safe Configuration Apply
Docker Compose 배포
Kubernetes / minikube 배포 구조
Actuator / Prometheus / Grafana 관측성 구성
Worker Graceful Shutdown
Smoke Test
```

---

## 3. 전체 아키텍처

전체 구조는 크게 Control Plane, Worker, Go Agent, Metadata DB, Target DB로 나눌 수 있습니다.

```text
사용자 / 운영자
  ↓
Spring Boot Control Plane API
  ↓
Metadata MySQL
  - database inventory
  - operation_job
  - operation_task
  - configuration profile
  - configuration snapshot
  - configuration drift
  - configuration apply result
  - backup restore verification
  - agent heartbeat / metrics

Spring Boot Worker
  ↓
Operation Job Claim
  ↓
OperationTask 생성 및 상태 반영

Go Agent
  ↓
Control Plane으로 outbound polling
  ↓
DB Host 내부 작업 실행
  - Linux 상태 수집
  - mysqldump 실행
  - restore verify 실행
  - 결과 보고

Target MySQL
  ↓
Health Check / Backup / Configuration Check / Apply 대상
```

이 프로젝트의 핵심은 Control Plane이 모든 작업을 직접 실행하지 않는다는 점입니다.  
Control Plane은 운영 요청을 저장하고 상태를 관리하며, 실제 Host 내부 작업은 Go Agent가 수행합니다.

---

## 4. 기술 스택

| 영역 | 사용 기술 |
|---|---|
| Backend | Java 21, Spring Boot 3.5 |
| Build | Gradle |
| Database | MySQL 8.4 |
| Agent | Go |
| Test | JUnit 5, Mockito, AssertJ, MockMvc |
| Integration Test | Docker Compose 기반 MySQL |
| Observability | Spring Boot Actuator, Prometheus, Grafana |
| Deployment | Docker Compose, Kubernetes, minikube |
| Logging | Logback, Request ID 기반 추적 |

---

## 5. 설계 방향

### 5.1 작은 기능부터 시작함

처음부터 전체 DB 운영 플랫폼을 만들지 않고, 단일 MySQL Health Check부터 시작했습니다.

초기 범위를 작게 잡은 이유는 다음 기반을 먼저 안정적으로 만들기 위해서입니다.

```text
DB 연결 구조
오류 분류
API 응답 형식
공통 예외 처리
요청 추적
로그 구조
테스트 구조
```

이후 DB Inventory, Job Engine, Agent, Backup, Configuration 기능으로 확장할 수 있도록 구조를 잡았습니다.

자세한 내용은 [프로젝트 개요](docs/1-프로젝트-개요.md) 문서에 정리되어 있습니다.

---

### 5.2 Health Check는 상태와 오류 원인을 분리함

DB 연결 결과는 단순 성공/실패가 아니라 `UP`, `DOWN` 상태로 표현했습니다.

연결 실패도 하나로 뭉개지 않고 다음처럼 분류했습니다.

```text
인증 실패
연결 거부
시간 초과
DNS 오류
기타 연결 오류
```

운영 관점에서는 “DB가 안 된다”보다 “왜 안 되는가”가 더 중요하기 때문입니다.

현재 Health Check API는 다음 형태입니다.

```http
GET /api/v1/databases/default/health
```

향후 DB Inventory가 확장되면 다음 형태로 확장될 수 있습니다.

```http
GET /api/v1/databases/{databaseId}/health
```

---

### 5.3 테스트는 책임별로 분리함

테스트는 다음 범위로 나누었습니다.

```text
오류 분류 단위 테스트
Service 단위 테스트
Controller / 공통 오류 응답 MVC 테스트
Filter 단위 테스트
실제 MySQL 연결 통합 테스트
```

일반 테스트와 통합 테스트를 분리한 이유는 Docker, Port, 계정, 비밀번호 같은 외부 환경 의존성 때문입니다.

```bash
./gradlew test
./gradlew integrationTest
```

빠른 검증은 단위 테스트로 처리하고, 실제 MySQL 연결 검증은 별도 통합 테스트로 분리했습니다.

자세한 내용은 [테스트 전략](docs/2-테스트-전략.md) 문서에 정리되어 있습니다.

---

## 6. Operation Job Engine

DB 백업, 설정 점검, 설정 변경 같은 운영 작업은 일반 조회 API와 성격이 다릅니다.

```text
실행 시간이 오래 걸릴 수 있음
중간에 실패할 수 있음
중복 실행되면 장애가 될 수 있음
재시도가 필요할 수 있음
작업 이력이 남아야 함
```

그래서 운영 요청을 즉시 실행하지 않고 Operation Job으로 저장한 뒤 Worker가 처리하도록 설계했습니다.

```text
운영 요청
  ↓
OperationJob 생성
  ↓
QUEUED 상태 저장
  ↓
Worker Claim
  ↓
RUNNING 상태 변경
  ↓
성공 / 실패 / 재시도 처리
  ↓
SUCCEEDED / FAILED / QUEUED 상태 저장
```

주요 설계 요소는 다음과 같습니다.

| 설계 요소 | 목적 |
|---|---|
| Job Status | 작업 상태를 명시적으로 관리 |
| Idempotency-Key | 동일 요청 중복 생성 방지 |
| Worker Claim | 여러 Worker 환경에서 작업 소유권 확보 |
| Lease | Worker 장애 시 RUNNING 고착 방지 |
| Retry | 재시도 가능한 실패와 불가능한 실패 구분 |
| Audit Log | 누가, 언제, 어떤 상태 변경을 만들었는지 기록 |

자세한 내용은 [비동기 운영 작업을 위한 Job Engine 설계](docs/3-비동기-운영-작업을-위한-job-engine-설계.md) 문서에 정리되어 있습니다.

---

## 7. Control Plane과 Go Agent 분리

DB Host 내부 작업은 Control Plane이 직접 수행하지 않고 Go Agent가 수행하도록 분리했습니다.

이 구조를 선택한 이유는 DB 서버에 inbound port를 열지 않기 위해서입니다.  
Agent는 Control Plane으로 outbound 요청을 보내며, Task를 polling해서 가져갑니다.

```text
Go Agent 등록
  ↓
Heartbeat 전송
  ↓
Task Polling
  ↓
Host 내부 작업 실행
  ↓
결과 보고
```

역할은 다음처럼 나누었습니다.

| 구성요소 | 역할 |
|---|---|
| Control Plane | 작업 생성, 상태 저장, 결과 조회 |
| Worker | OperationJob Claim, OperationTask 생성 |
| Go Agent | Host 내부에서 실제 명령 실행 |
| Metadata DB | Job, Task, Agent, 결과 이력 저장 |
| Target DB | 실제 운영 대상 DB |

이 구조를 통해 Control Plane은 중앙 관리 역할에 집중하고, Agent는 DB Host 가까이에서 제한된 작업만 수행합니다.

자세한 내용은 [Control Plane과 Go Agent를 분리한 DB Host 작업 실행 구조](docs/4-control-plane과-go-agent를-분리한-db-host-작업-실행-구조.md) 문서에 정리되어 있습니다.

---

## 8. OperationJob과 OperationTask 연결

초기 Agent 구조에서는 Agent가 Task를 가져갈 수 있었지만, 상위 운영 요청인 OperationJob과 Task의 연결이 약했습니다.

이를 개선하여 다음 흐름을 만들었습니다.

```text
Backup Job 생성
  ↓
Worker가 Job Claim
  ↓
OperationTask 생성
  ↓
Go Agent가 Task Polling
  ↓
Go Agent가 mysqldump 실행
  ↓
백업 파일 검증
  ↓
Task 완료 보고
  ↓
OperationJob 성공 / 실패 반영
```

이렇게 한 이유는 사용자가 궁금해하는 것은 개별 Task 성공 여부가 아니라, “백업 운영 요청이 성공했는가”이기 때문입니다.

현재 구조에서는 OperationTask의 실행 결과가 OperationJob에 반영됩니다.

```text
OperationJob: BACKUP
  ↓
OperationTask: MYSQL_LOGICAL_BACKUP
  ↓
Go Agent 실행
  ↓
OperationJob 상태 반영
```

자세한 내용은 [Operation Job과 Operation Task를 연결한 Agent 기반 백업 실행 구조](docs/5-operation-job과-operation-task를-연결한-agent-기반-백업-실행-구조.md) 문서에 정리되어 있습니다.

---

## 9. 백업 성공 기준 확장

기존 백업 성공 기준은 파일 생성 중심이었습니다.

```text
mysqldump 실행
  ↓
.sql 파일 생성
  ↓
파일 크기 확인
  ↓
checksum 생성
  ↓
Backup Job 성공
```

하지만 운영 관점에서 더 중요한 것은 파일이 존재하는지가 아니라, 장애 상황에서 실제로 복원할 수 있는지입니다.

그래서 백업 성공 기준을 복원 가능성까지 확장했습니다.

```text
mysqldump 실행
  ↓
.sql 파일 생성
  ↓
임시 DB 생성
  ↓
백업 파일 Restore
  ↓
테이블 존재 여부 확인
  ↓
Row Count 확인
  ↓
복원 검증 결과 저장
  ↓
Backup Job 성공 또는 실패 처리
```

변경 후에는 `MYSQL_LOGICAL_BACKUP` Task가 성공해도 Job을 바로 성공 처리하지 않습니다.  
`MYSQL_RESTORE_VERIFY` Task까지 통과해야 Backup Job이 최종 성공합니다.

자세한 내용은 [백업 성공 기준을 “파일 생성”에서 “복원 가능성”으로 확장하기](docs/8-백업-성공기준을-확장시키기.md) 문서에 정리되어 있습니다.

---

## 10. Configuration Drift

Configuration Drift는 DB의 실제 설정값이 운영 기준과 달라지는 문제를 다룹니다.

예를 들어 다음과 같은 설정은 DB가 연결 가능하더라도 운영 품질에 영향을 줄 수 있습니다.

```text
slow_query_log
long_query_time
binlog_format
max_connections
```

Configuration Drift 기능은 다음 질문에 답하기 위해 설계했습니다.

```text
이 DB는 우리가 정의한 운영 기준과 일치하는가?
일치하지 않는다면 어떤 설정이 다른가?
expectedValue와 actualValue는 무엇인가?
언제부터 달라졌는가?
다시 기준에 맞게 변경할 수 있는 항목인가?
```

전체 흐름은 다음과 같습니다.

```text
Configuration Profile 생성
  ↓
Profile Parameter 등록
  ↓
Configuration Check Job 생성
  ↓
Worker가 Job Claim
  ↓
실제 DB 설정값 수집
  ↓
Configuration Snapshot 저장
  ↓
Profile Parameter와 Snapshot 비교
  ↓
Configuration Drift 저장
  ↓
Drift API 조회
```

Drift 탐지는 읽기 중심 작업입니다.  
실제 DB 설정을 변경하지 않고, 기준과 실제값의 차이를 탐지하고 기록하는 데 집중했습니다.

자세한 내용은 [Configuration Drift 설계](docs/6-configuration-draft-설계.md) 문서에 정리되어 있습니다.

---

## 11. Safe Configuration Apply

Configuration Drift가 “기준과 실제값의 차이”를 찾는 기능이라면, Safe Configuration Apply는 설정 변경을 안전한 운영 작업으로 처리하는 기능입니다.

일반적인 수동 설정 변경은 다음 문제가 있습니다.

```text
누가 변경했는지 불명확함
왜 변경했는지 기록이 부족함
변경 전 값이 남지 않음
변경 후 검증이 빠질 수 있음
잘못된 설정값을 넣을 수 있음
동시에 두 명이 같은 설정을 바꿀 수 있음
Static Parameter를 Dynamic처럼 바꾸려 할 수 있음
장애 발생 시 Rollback 근거가 부족함
```

그래서 설정 변경도 즉시 실행하지 않고 OperationJob으로 생성한 뒤 Worker가 안전한 순서로 처리하도록 설계했습니다.

```text
Apply 요청
  ↓
ProfileParameter 기준 검증
  ↓
dynamic=true 검증
  ↓
applyAllowed=true 검증
  ↓
ParameterValueType별 targetValue 검증
  ↓
동일 DB Apply 중복 실행 차단
  ↓
변경 전 Snapshot 저장
  ↓
MySQL SET GLOBAL 실행
  ↓
변경 후 Snapshot 저장
  ↓
실제 반영 여부 검증
  ↓
Apply 결과 저장
```

Safe Apply의 핵심은 단순히 `SET GLOBAL`을 실행하는 것이 아니라, 설정 변경을 추적 가능하고 검증 가능한 운영 Job으로 모델링하는 것입니다.

자세한 내용은 [Safe Configuration Apply 설계](docs/7-safe-configuration-apply-설계.md) 문서에 정리되어 있습니다.

---

## 12. 배포 및 관측성

운영 관리 플랫폼은 단순히 로컬에서 API가 실행되는 것만으로는 부족합니다.  
배포 후 상태, 작업 처리량, 실패율, Agent 상태, Worker 상태를 관측할 수 있어야 합니다.

현재 배포 및 관측성 구성은 다음을 포함합니다.

```text
Docker Compose 기반 로컬 실행
API / Worker 역할 분리
Metadata MySQL
Target MySQL
Go Agent
Actuator health / readiness / liveness
Prometheus metrics scrape
Grafana dashboard 자동 구성
Kubernetes manifest
ConfigMap / Secret / Deployment / StatefulSet / Service
Worker Graceful Shutdown
Smoke Test
```

Docker Compose 환경은 로컬 개발과 빠른 통합 테스트에 사용합니다.

```text
API
Worker
Metadata MySQL
Target MySQL
Agent
Prometheus
Grafana
```

Kubernetes 환경은 minikube를 기준으로 배포 구조를 검증합니다.

```text
Namespace
ConfigMap
Secret
Deployment
StatefulSet
Service
Kustomize
Probe
```

자세한 내용은 [DB FleetOps 배포 및 관측성 구성 정리](docs/9-db-fleetops-배포-및-관측성-구성.md) 문서에 정리되어 있습니다.

---

## 13. 문서 목록

프로젝트의 각 단계별 설계 문서는 `docs` 디렉토리에 정리되어 있습니다.

| 번호 | 문서 | 설명 |
|---:|---|---|
| 1 | [프로젝트 개요](docs/1-프로젝트-개요.md) | 프로젝트 목적, 현재 구현 범위, API, 기술 스택, 패키지 구조 정리 |
| 2 | [테스트 전략](docs/2-테스트-전략.md) | 단위 테스트, MVC 테스트, 통합 테스트를 분리한 이유와 실행 방식 정리 |
| 3 | [비동기 운영 작업을 위한 Job Engine 설계](docs/3-비동기-운영-작업을-위한-job-engine-설계.md) | OperationJob, 상태 전이, Idempotency, Worker Claim, Lease, Retry, Audit 설계 |
| 4 | [Control Plane과 Go Agent를 분리한 DB Host 작업 실행 구조](docs/4-control-plane과-go-agent를-분리한-db-host-작업-실행-구조.md) | Control Plane과 Agent를 분리한 이유, Pull 방식 통신, Agent Task 구조 정리 |
| 5 | [Operation Job과 Operation Task를 연결한 Agent 기반 백업 실행 구조](docs/5-operation-job과-operation-task를-연결한-agent-기반-백업-실행-구조.md) | Backup Job, OperationTask, Go Agent 실행, mysqldump, 결과 반영 흐름 정리 |
| 6 | [Configuration Drift 설계](docs/6-configuration-draft-설계.md) | Configuration Profile, Snapshot, Drift 비교, Drift 저장 및 조회 구조 정리 |
| 7 | [Safe Configuration Apply 설계](docs/7-safe-configuration-apply-설계.md) | 설정 변경 검증, SET GLOBAL, 변경 전후 Snapshot, Apply 결과 저장 구조 정리 |
| 8 | [백업 성공 기준을 “파일 생성”에서 “복원 가능성”으로 확장하기](docs/8-백업-성공기준을-확장시키기.md) | 백업 파일 생성 이후 Restore 검증까지 성공 기준을 확장한 이유와 구현 정리 |
| 9 | [DB FleetOps 배포 및 관측성 구성 정리](docs/9-db-fleetops-배포-및-관측성-구성.md) | Docker Compose, Kubernetes, Actuator, Prometheus, Grafana, Smoke Test 구성 정리 |

---

## 14. 실행 및 테스트

### 14.1 단위 테스트

```bash
./gradlew test
```

### 14.2 통합 테스트

통합 테스트는 실제 MySQL 환경에 의존하므로 일반 테스트와 분리되어 있습니다.

```bash
./gradlew integrationTest
```

통합 테스트 실행 전 환경변수를 적용합니다.

```bash
set -a
source .env
set +a
```

### 14.3 Docker Compose 로그 확인

실행 중 오류 로그를 확인할 때는 다음 명령을 사용할 수 있습니다.

```bash
docker compose logs -f --tail=200 | grep --color=always -Ei "error|failed|exception|fatal"
```

---

## 15. 프로젝트에서 중점적으로 고민한 부분

이 프로젝트는 단순 CRUD API를 만드는 것보다 운영 상황에서 문제가 될 수 있는 지점을 구조로 풀어내는 데 초점을 두었습니다.

특히 다음 부분을 중요하게 다루었습니다.

```text
긴 작업을 HTTP 요청 안에서 직접 실행하지 않기
중복 요청으로 같은 운영 작업이 여러 번 생성되지 않게 하기
Worker가 죽어도 작업 상태가 고착되지 않게 하기
Agent가 임의 명령을 실행하지 않게 하기
DB Host에 inbound port를 열지 않기
백업 파일 생성만으로 성공 처리하지 않기
설정 변경 전후 값을 남기기
읽기 중심 점검과 쓰기 중심 변경을 분리하기
테스트와 통합 테스트를 분리하기
배포 후 상태를 관측할 수 있게 하기
```

---

## 16. 현재 한계와 개선 방향

현재 프로젝트는 포트폴리오와 학습 목적의 MVP 성격이 강합니다.  
따라서 다음 개선 여지가 남아 있습니다.

```text
DB Inventory 기반 다중 DB 관리 고도화
PostgreSQL 등 다른 DBMS Adapter 확장
Testcontainers 기반 통합 테스트 자동화
Agent 인증 및 Token Rotation 강화
대용량 DB 백업 검증 전략 개선
Configuration Apply 승인 프로세스 추가
Rollback 전략 구체화
Alert 연동
CI/CD Pipeline 구성
운영 Dashboard 고도화
```