# 비동기 운영 작업을 위한 Job Engine 설계

## 1. 결론

백업과 설정 적용처럼 오래 걸리고 실패할 수 있는 작업은 HTTP 요청 안에서 바로 실행하지 않습니다. 먼저 `OperationJob`으로 저장하고 Worker가 나중에 처리합니다.

```text
사용자 요청
  → OperationJob 저장
  → 즉시 Job ID 반환
  → Worker가 Job 선점
  → 작업 실행
  → 최종 상태 저장
```

이 구조를 선택한 이유는 다음과 같습니다.

- 사용자가 작업이 끝날 때까지 HTTP 연결을 유지하지 않아도 됩니다.
- Control Plane이 재시작되어도 요청과 상태가 Metadata DB에 남습니다.
- 같은 요청의 중복 생성과 일시적인 실패를 제어할 수 있습니다.
- 운영자는 Job ID로 진행 상태와 결과를 다시 조회할 수 있습니다.

이 문서는 Job Engine만 설명합니다. Control Plane과 Agent를 분리한 이유는 [4번 문서](4-중앙-관제-서버와-Go-Agent를-분리한-이유.md), Job이 Agent Task로 이어지는 백업 흐름은 [5번 문서](5-operation-job과-operation-task를-연결한-agent-기반-백업-실행-구조.md)에서 설명합니다.

## 2. Job이 맡은 일

`OperationJob`은 사용자가 달성하려는 하나의 운영 목적입니다.

```text
OperationJob
  - 어떤 작업인가
  - 어느 Database가 대상인가
  - 누가 요청했는가
  - 지금 어떤 상태인가
  - 재시도는 몇 번 했는가
  - 최종 결과는 무엇인가
```

Worker는 실행 가능한 Job을 찾아 실행 순서와 상태를 조정합니다. 실제 Host 작업이 필요하면 하위 `OperationTask`를 만들 수 있지만, 그 세부 구조는 Job Engine의 책임이 아닙니다.

쉽게 말하면 Job은 사용자가 보는 주문서이고, Worker는 처리할 주문서를 가져가는 조정자입니다.

## 3. 왜 먼저 저장하는가

백업이 20분 걸린다고 해서 사용자가 화면을 20분 동안 열어 둘 필요는 없습니다. Job을 먼저 저장하면 API 연결과 작업의 생명주기를 분리할 수 있습니다.

| 구분 | HTTP 요청에서 바로 실행 | Job으로 저장한 뒤 실행 |
|---|---|---|
| 응답 | 실제 작업이 끝날 때까지 기다림 | Job 접수 결과를 먼저 반환 |
| 상태 추적 | 요청 Log에 의존하기 쉬움 | Job 상태로 조회 가능 |
| Control Plane 재시작 | 실행 정보를 잃기 쉬움 | Metadata DB에 Job이 남음 |
| 일시 실패 | 호출자가 다시 요청 | 같은 Job을 재대기 가능 |
| 중복 요청 | 같은 작업이 여러 번 생성될 수 있음 | Idempotency Key로 기존 Job 조회 |

아주 짧고 실패 가능성이 낮은 조회까지 Job으로 만들 필요는 없습니다. 오래 걸리거나 결과 추적이 필요한 운영 작업에만 사용합니다.

## 4. Job 상태를 어떻게 관리하는가

Job은 `QUEUED` 상태로 생성됩니다.

```text
QUEUED
  → RUNNING
      → SUCCEEDED
      → FAILED → QUEUED
      → TIMED_OUT

QUEUED | RUNNING | FAILED | TIMED_OUT
  → CANCELLED
```

각 상태의 의미는 다음과 같습니다.

| 상태 | 의미 |
|---|---|
| `QUEUED` | Worker가 가져갈 수 있는 상태 |
| `RUNNING` | Worker가 실행을 조정 중인 상태 |
| `SUCCEEDED` | 운영 목적을 달성한 상태 |
| `FAILED` | 작업이 실패한 상태 |
| `TIMED_OUT` | 허용된 실행 시간을 넘긴 상태 |
| `CANCELLED` | 더 이상 실행하지 않기로 한 상태 |

상태 변경은 Service에서 필드를 직접 조합하지 않고 `OperationJob.start(...)`, `succeed(...)`, `fail(...)`, `retry(...)`, `timeout(...)`, `cancel(...)` 같은 도메인 메서드로 수행합니다.

따라서 아직 Worker가 가져가지 않은 Job을 성공 처리하거나, 이미 성공한 Job을 다시 시작하는 잘못된 흐름을 한곳에서 거절할 수 있습니다.

## 5. 중복 요청을 어떻게 막는가

사용자가 버튼을 두 번 누르거나 응답을 받지 못해 같은 요청을 다시 보낼 수 있습니다. 첫 번째 요청이 이미 저장되었다면 새로운 Job을 하나 더 만들면 안 됩니다.

현재는 다음 값으로 같은 요청을 찾습니다.

```text
targetDatabaseId + jobType + idempotencyKey
```

같은 조합이 있으면 새 Job을 만들지 않고 기존 Job을 반환합니다. Database 고유 제약도 동시에 들어오는 중복 요청을 한 번 더 막습니다.

다만 요청마다 다른 Key를 보내거나 Key를 생략하면 중복을 막을 수 없습니다. Key를 언제 만들고 언제까지 재사용할지는 API 호출자가 지켜야 하는 계약입니다.

## 6. Worker는 Job을 어떻게 가져가는가

Worker는 다음 조건을 만족하는 Job을 찾습니다.

1. 상태가 `QUEUED`입니다.
2. `availableAt`이 현재 시각보다 이전입니다.
3. 우선순위가 높은 Job을 먼저 선택합니다.
4. 우선순위가 같으면 먼저 생성된 Job을 선택합니다.

선택한 Job은 `RUNNING`으로 바뀌고 Worker 소유 정보가 저장됩니다.

```text
leaseOwner = worker-1
leaseUntil = 현재 시각 + Lease 시간
```

코드는 다음 역할로 나누었습니다.

- `OperationWorkerController`가 Worker의 Job 가져오기·완료·실패 요청을 받습니다.
- `JobClaimService`가 실행 가능한 Job을 선택하고 실행권을 설정합니다.
- `JobExecutionDispatcher`가 Job 종류에 맞는 `JobExecution`을 선택합니다.
- `JobReportService`가 Worker의 성공·실패 보고를 반영합니다.
- `JobStore`가 조건에 맞는 Job을 조회합니다.
- `OperationJob.start(...)`가 상태와 Worker Lease를 설정합니다.

Controller는 HTTP 요청만 받고, Service는 흐름을 조정하며, Job 객체는 상태 규칙을 관리합니다.

## 7. Job Lease의 현재 한계

Lease는 Worker가 사라졌을 때 언제 소유권을 다시 판단할지 알려주는 기준입니다. 하지만 현재 Job Engine은 Claim할 때 Lease를 저장하고 결과를 받을 때 Worker를 확인하는 수준입니다.

아직 다음 기능은 구현하거나 충분히 실험하지 않았습니다.

- 실행 중인 Job Lease의 주기적 갱신
- 만료된 `RUNNING` Job을 찾는 Scheduler
- 만료 Job의 `QUEUED` 복귀 또는 `TIMED_OUT` 판정
- 여러 Worker가 동시에 같은 Job을 Claim하는 상황의 충분한 경쟁 실험

Agent가 실행하는 `OperationTask` Lease 회수는 별도 구현되어 있습니다. Task Lease와 Job Lease는 목적이 다르므로 이 문서에서 같은 기능으로 설명하지 않습니다.

## 8. Retry는 어떻게 처리하는가

Retry는 새 Job을 만드는 것이 아니라 실패한 Job을 다시 `QUEUED`로 돌리는 행위입니다.

```text
RUNNING
  → FAILED
  → retryCount 증가
  → availableAt을 미래 시각으로 설정
  → Worker 소유 정보 제거
  → QUEUED
```

현재 Worker 흐름은 실패 요청의 `retryable` 값과 남은 재시도 횟수를 확인합니다. 재시도할 수 있으면 잠시 기다린 뒤 같은 Job을 다시 실행 대상으로 만듭니다.

일시적인 Network 오류에는 Retry가 도움이 될 수 있습니다. 반면 잘못된 Credential이나 존재하지 않는 Database처럼 같은 입력으로 다시 실패할 문제는 반복하지 않아야 합니다.

현재 Retry 판단은 오류 코드별 정책으로 충분히 세분화되어 있지 않습니다. 호출자가 전달한 `retryable` 값에 의존하는 부분은 후속 보완이 필요합니다.

## 9. Audit Log를 남기는 이유

현재 상태만 보면 Job이 처음부터 대기 중인지, 실패한 뒤 재시도를 기다리는지 알기 어렵습니다.

```text
JOB_CREATED
  → JOB_CLAIMED
  → JOB_FAILED
  → JOB_RETRIED
```

Audit Log는 Job이 현재 상태에 도달한 과정을 보여줍니다. Operation 영역은 저장 방법을 직접 알지 않고 `AuditRecorderPort`를 호출합니다.

```text
Operation Service
  → AuditRecorderPort
  → Audit 저장 구현
```

이렇게 나누면 Job 처리 규칙과 Audit 저장 책임이 섞이지 않습니다. 대신 Audit에는 Credential, 전체 Payload나 불필요한 대용량 결과가 기록되지 않도록 별도 기준이 필요합니다.

## 10. 왜 Metadata DB를 Queue로 사용하는가

현재는 Kafka나 별도 Job Queue를 두지 않고 Metadata DB에서 실행 가능한 Job을 조회합니다.

장점은 다음과 같습니다.

- 추가 운영 구성요소가 필요하지 않습니다.
- Job 상태와 결과를 같은 Database에서 조회할 수 있습니다.
- Transaction, Version과 고유 제약을 사용할 수 있습니다.
- 초기 기능과 테스트가 단순합니다.

대신 Job 수와 Worker 경쟁이 커지면 조회 충돌과 Database 부하가 증가할 수 있습니다. 완료 Job 보관 기간, 인덱스, 정리 정책과 여러 Worker의 Claim 방식도 필요해집니다.

현재 규모에서는 단순성이 더 중요하다고 판단했으며, 실제 부하 측정 없이 별도 Queue 도입이 필요하다고 단정하지 않습니다.

## 11. 코드 구성

```text
operation/
  ├─ api/
  │   ├─ OperationJobController
  │   └─ OperationWorkerController
  ├─ application/
  │   ├─ JobService
  │   ├─ JobClaimService
  │   ├─ JobReportService
  │   └─ JobExecution
  ├─ domain/
  │   └─ OperationJob
  └─ adapter/
      └─ persistence/JobStoreAdapter
```

- API는 요청 형식과 응답을 담당합니다.
- Application Service는 한 유스케이스의 실행 순서를 담당합니다.
- `OperationJob`은 허용되는 상태 변경을 담당합니다.
- Repository는 저장과 실행 대상 조회를 담당합니다.

Job Type별 실제 실행 방식이나 Agent Task 생성은 각 기능 문서에서 설명합니다.

## 12. 테스트로 확인한 내용

현재 자동 테스트에서 다음 내용을 확인합니다.

- Job이 `QUEUED`로 생성됩니다.
- 같은 Idempotency Key 요청은 기존 Job을 반환합니다.
- Worker가 실행 가능한 Job을 Claim하면 `RUNNING`이 됩니다.
- 잘못된 Worker가 완료·실패 결과를 저장할 수 없습니다.
- 성공·실패·Retry·취소 상태 전이 규칙이 적용됩니다.
- 재시도하면 횟수와 다음 실행 가능 시각이 변경됩니다.
- BACKUP Job Claim이 하위 Task 생성으로 이어집니다.
- 주요 Job 행위가 Audit Port에 전달됩니다.

이 테스트는 도메인과 Application Service 중심입니다. 여러 Worker Process의 실제 동시 경쟁, Worker 강제 종료와 Job Lease 자동 회수는 아직 별도의 장애 실험이 필요합니다.

## 13. 최종 판단

현재 Job Engine은 운영 요청을 HTTP 연결과 분리하고, 상태와 재시도를 Metadata DB에 남기는 초기 구조로는 적합합니다.

이미 해결한 부분은 다음과 같습니다.

- 운영 요청의 비동기 접수와 상태 조회
- 도메인 상태 전이
- Idempotency Key 기반 중복 접수 방지
- Worker Claim과 소유자 확인
- 제한된 Retry와 Audit 기록

보완이 필요한 핵심은 Job Lease의 갱신·자동 회수와 여러 Worker가 경쟁하는 실제 환경 검증입니다. 이 기능이 확인되기 전에는 Worker 장애가 발생해도 Job이 자동으로 복구된다고 주장하면 안 됩니다.
