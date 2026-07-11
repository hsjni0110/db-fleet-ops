비동기 운영 작업을 위한 Job Engine 설계

# 1. 왜 Operation Job이 필요한가

DB FleetOps는 단순히 DB 상태를 조회하는 모니터링 도구가 아니라, 여러 DB 인스턴스의 운영 작업을 플랫폼에서 안전하게 처리하는 것을 목표로 합니다.

DB 운영 작업에는 백업, 설정 점검, 설정 변경, 재시작 같은 작업이 포함될 수 있습니다. 이런 작업은 일반적인 조회 API와 성격이 다릅니다. 실행 시간이 오래 걸릴 수 있고, 중간에 실패할 수 있으며, 같은 작업이 중복 실행되면 운영 장애로 이어질 수 있습니다.

예를 들어 사용자가 백업 요청 버튼을 눌렀는데 네트워크 응답이 늦어 다시 버튼을 누르면 동일한 백업 작업이 두 번 생성될 수 있습니다. Worker가 작업을 가져간 뒤 서버가 종료되면 해당 작업은 끝나지도 않았는데 계속 실행 중인 것처럼 남을 수 있습니다. 또한 작업이 실패했을 때 재시도 가능한 오류인지, 즉시 실패 처리해야 하는 오류인지 구분하지 않으면 운영자가 수동으로 상태를 정리해야 합니다.

이 문제를 해결하기 위해 Operation Job 구조를 도입했습니다.

# 2. Operation Job의 기본 개념

Operation Job은 운영 요청을 즉시 실행하지 않고, 먼저 작업 단위로 저장한 뒤 Worker가 처리하도록 만든 구조입니다.

기본 흐름은 다음과 같습니다.

```
운영 요청
  ↓
Job 생성
  ↓
QUEUED 상태로 저장
  ↓
Worker가 Claim
  ↓
RUNNING 상태로 변경
  ↓
성공 또는 실패 처리
  ↓
SUCCEEDED / FAILED / QUEUED 상태 저장
```

이 구조를 사용하면 HTTP 요청과 실제 작업 실행을 분리할 수 있습니다. 사용자는 작업 요청이 접수되면 Job ID를 받고, 이후 Job 상태를 조회합니다. 서버는 오래 걸리는 작업을 HTTP 요청 안에서 직접 처리하지 않아도 됩니다.

# 3. 상태를 명시적으로 관리한 이유

Operation Job에서 가장 중요한 것은 상태입니다.

현재 정의한 상태는 다음과 같습니다.

- QUEUED
- RUNNING
- SUCCEEDED
- FAILED
- CANCELLED
- TIMED_OUT

각 상태의 의미는 다음과 같습니다.

- QUEUED는 실행 대기 상태입니다. Job은 생성되었지만 아직 Worker가 가져가지 않았습니다.
- RUNNING은 Worker가 Job을 가져가 처리 중인 상태입니다.
- SUCCEEDED는 작업이 정상 완료된 상태입니다.
- FAILED는 작업이 실패한 상태입니다.
- CANCELLED는 사용자가 취소한 상태입니다.
- TIMED_OUT은 Worker가 작업을 가져갔지만 일정 시간 안에 완료하지 못한 상태를 표현하기 위해 준비한 상태입니다.

상태를 문자열이나 boolean 값으로 대충 처리하지 않고 enum으로 정의한 이유는 잘못된 상태 전이를 막기 위해서입니다. 예를 들어 이미 성공한 Job이 다시 실행되거나, 아직 실행되지 않은 Job이 성공 처리되는 상황은 허용하면 안 됩니다.

그래서 OperationJob 도메인 안에 상태 전이 메서드를 두었습니다.

- QUEUED → RUNNING
- RUNNING → SUCCEEDED
- RUNNING → FAILED
- FAILED → QUEUED

허용되지 않는 전이는 예외로 막습니다. 이 방식은 상태 변경 규칙을 Service 곳곳에 흩뿌리지 않고, Job 도메인 내부에 모아두기 위한 선택입니다.

# 4. Job 생성 시 QUEUED로 시작하는 이유

Job은 생성되자마자 실행하지 않습니다.
생성 시 상태는 항상 QUEUED입니다.

```
POST backup 요청
  ↓
OperationJob 생성
  ↓
status = QUEUED
```

이렇게 한 이유는 API 요청과 작업 실행을 분리하기 위해서입니다. 백업이나 설정 변경 같은 작업을 HTTP 요청 안에서 바로 실행하면 요청 시간이 길어지고, 서버가 중간에 종료되었을 때 복구하기도 어렵습니다.

반대로 Job을 먼저 저장하면 작업 요청 자체는 짧게 끝낼 수 있고, Worker가 별도로 처리할 수 있습니다.

# 5. Idempotency-Key를 적용한 이유

운영 작업은 중복 실행되면 위험합니다.

예를 들어 같은 DB에 대해 동일한 백업 Job이 두 번 생성되면 Worker는 두 작업을 모두 처리하려고 할 수 있습니다. 백업은 비교적 안전한 작업이지만, 설정 변경이나 재시작 작업이라면 중복 실행 자체가 장애 원인이 될 수 있습니다.

그래서 Job 생성 API에는 Idempotency-Key를 받도록 했습니다.

중복 판단 기준은 다음과 같습니다.

```
targetDatabaseId + jobType + idempotencyKey
```

같은 조합의 Job이 이미 있으면 새 Job을 만들지 않고 기존 Job을 반환합니다.

```
첫 번째 요청
  ↓
새 Job 생성
동일한 Idempotency-Key로 재요청
  ↓
기존 Job 반환
```

Service에서 먼저 기존 Job을 조회하고, DB 레벨에서도 Unique Constraint를 둔 이유는 동시 요청 때문입니다. 애플리케이션에서 조회 후 저장하는 방식만 사용하면 동시에 두 요청이 들어왔을 때 둘 다 기존 Job이 없다고 판단할 수 있습니다. DB 제약 조건은 이런 경쟁 상황을 마지막에 한 번 더 막아주는 안전장치입니다.


# 6. Worker Claim의 의미

Claim은 Worker가 대기 중인 Job 하나를 가져가겠다는 의미입니다.

다만 실제로 큐에서 데이터를 삭제하는 것은 아닙니다. DB에 저장된 Job의 상태를 QUEUED에서 RUNNING으로 변경합니다.

```
QUEUED Job
  ↓ claim
RUNNING Job
```

이렇게 상태를 변경하면 다른 Worker는 해당 Job을 다시 가져가면 안 됩니다.

현재 구현은 JPA 조회 기반으로 단순하게 시작했습니다. 이후 Worker가 여러 개로 늘어나면 MySQL의 FOR UPDATE SKIP LOCKED를 사용해 동시 Claim 문제를 더 안전하게 처리할 수 있습니다.

`FOR UPDATE SKIP LOCKED`를 쓰는 이유는 Job Queue의 Claim을 DB row lock 기반의 원자적 연산으로 만들기 위해서입니다. 

FOR UPDATE는 같은 Job을 두 Worker가 동시에 가져가지 못하게 하고, SKIP LOCKED는 이미 다른 Worker가 잡은 Job에서 대기하지 않고 다음 Job으로 넘어가게 해서 Worker 수가 늘어났을 때 lock contention과 head-of-line blocking을 줄입니다. 

실제 작업 중에는 lock을 잡지 않고, claim 순간에만 짧게 lock을 사용하고 lease_until으로 장애 복구를 처리하는 구조입니다.

현재 단계에서 JPA 기반으로 먼저 구현한 이유는 Job Engine의 도메인 흐름을 먼저 검증하기 위해서입니다. 처음부터 동시성 제어까지 모두 넣으면 구현 복잡도가 커지고, 상태 전이와 API 흐름을 검증하기 어려워집니다.

# 7. Lease를 둔 이유

Worker가 Job을 Claim하면 leaseOwner와 leaseUntil을 설정합니다.

```
leaseOwner = worker-1
leaseUntil = now + 60 seconds
```

Lease는 Worker가 해당 Job을 점유할 수 있는 유효 시간입니다.

Lease가 필요한 이유는 Worker 장애 때문입니다. Worker가 Job을 가져간 뒤 서버가 종료되면 Job은 RUNNING 상태로 남습니다. Lease가 없다면 이 Job은 영원히 실행 중인 상태로 방치될 수 있습니다.

Lease가 있으면 이후 다음과 같은 복구가 가능합니다.

```
RUNNING 상태
  ↓
leaseUntil 지남
  ↓
Worker 장애로 판단
  ↓
TIMED_OUT 처리 또는 다시 QUEUED 전환
```

현재 구현에서는 Lease 설정까지만 했고, Lease 만료 복구는 다음 단계로 남겨두었습니다. 그래도 필드를 먼저 둔 이유는 Worker 기반 구조에서 반드시 필요한 복구 지점을 미리 설계에 반영하기 위해서입니다.

# 8. Job 완료 처리에서 Worker 소유권을 확인한 이유

Job을 성공 또는 실패 처리할 때는 두 가지 조건을 확인합니다.

```
status == RUNNING
leaseOwner == workerId
```

이 검증이 필요한 이유는 아무 Worker나 Job을 완료 처리하면 안 되기 때문입니다.

예를 들어 worker-1이 Job을 Claim했는데 worker-2가 실수로 성공 처리 API를 호출하면 실제 실행 주체와 결과 기록이 어긋납니다. 운영 작업에서는 누가 작업을 가져갔고, 누가 완료 처리했는지가 중요합니다.

그래서 완료 처리에서는 Job을 Claim한 Worker만 성공 또는 실패 처리할 수 있도록 했습니다.


# 9. Retry를 별도 상태 전이로 처리한 이유

작업 실패는 모두 같은 실패가 아닙니다.

일시적인 네트워크 오류, 대상 DB의 순간적인 부하, 외부 명령 실행 실패처럼 재시도할 가치가 있는 오류가 있습니다. 반면 잘못된 Credential, 존재하지 않는 DB, 권한 부족처럼 재시도해도 해결되지 않는 오류도 있습니다.

그래서 실패 요청에는 retryable 값을 받도록 했습니다.

```
{
  "resultCode": "BACKUP_FAILED",
  "resultMessage": "temporary mysqldump error",
  "retryable": true
}
```

`retryable=true`이고 `retryCount < maxRetryCount`이면 Job은 다시 QUEUED 상태로 돌아갑니다.

```
RUNNING
  ↓ fail
FAILED
  ↓ retry
QUEUED
```

Retry 시에는 다음 값을 정리합니다.

- retryCount 증가
- availableAt 재설정
- leaseOwner 제거
- leaseUntil 제거
- finishedAt 제거

이렇게 해야 Worker가 나중에 다시 Job을 가져갈 수 있습니다. availableAt을 둔 이유는 실패 직후 바로 재시도하지 않고 일정 시간 뒤에 다시 처리하기 위해서입니다. 현재는 단순히 30초 뒤로 설정했지만, 이후에는 retryCount에 따라 Backoff 시간을 늘릴 수 있습니다.

# 10. Audit Log를 남긴 이유

운영 작업은 결과만 중요한 것이 아닙니다.

누가 작업을 만들었는지, 어떤 Worker가 가져갔는지, 언제 실패했는지, 재시도되었는지 같은 이력이 남아야 합니다. 장애가 발생했을 때 원인을 추적하려면 상태값만으로는 부족합니다.

현재는 다음 이벤트를 Audit Log로 기록합니다.

- JOB_CREATED
- JOB_CLAIMED
- JOB_SUCCEEDED
- JOB_FAILED
- JOB_RETRIED

Audit Log는 수정하지 않는 append-only 구조로 설계했습니다. 운영 이력은 나중에 변경되면 안 되기 때문입니다.

# 11. Audit을 Port로 분리한 이유

처음에는 OperationJobService가 AuditLogService를 직접 호출하는 방식도 가능했습니다. 하지만 이 방식은 application service끼리 직접 의존하게 만듭니다.

현재 프로젝트에서는 Port & Adapter 구조를 사용하고 있으므로 Audit도 같은 기준을 적용했습니다.

```
OperationJobService
        │
        ▼
AuditRecorderPort
        │
        ▼
AuditRecorderService
        │
        ▼
AuditLogRepository
```

Operation 모듈 입장에서는 "Audit을 기록한다"는 인터페이스만 알면 됩니다. 실제 저장 방식이 JPA인지, 파일인지, Kafka인지 알 필요가 없습니다.

이 구조를 선택한 이유는 다음과 같습니다.

첫째, Operation 모듈이 Audit 구현 방식에 직접 묶이지 않습니다.

둘째, 이후 Audit 저장소를 바꾸더라도 Operation Job 로직을 크게 바꾸지 않아도 됩니다.

셋째, 테스트에서 Audit 기록을 Mock으로 대체하기 쉽습니다.

이 구조는 작은 프로젝트에서는 다소 과해 보일 수 있지만, 운영 플랫폼이라는 목적을 생각하면 관심사를 분리하는 편이 더 적절하다고 판단했습니다.

# 12. 테스트를 어떻게 나누었는가

OperationJobTest는 순수 도메인 테스트입니다. 상태 전이가 올바른지, 잘못된 상태 전이가 막히는지 확인합니다.

OperationJobServiceTest는 Job 생성과 Idempotency 처리를 검증합니다. 활성 DB에 대해서만 Job이 생성되는지, 같은 Idempotency-Key로 요청했을 때 기존 Job을 반환하는지 확인합니다.

OperationWorkerServiceTest는 Worker Claim, 성공 처리, 실패 처리, Retry 처리, Worker 소유권 검증을 확인합니다.

ControllerTest는 HTTP 경로, 상태코드, JSON 응답 구조를 검증합니다.

PersistenceTest는 JPA를 통해 Job이 실제로 저장되고, 상태 변경이 영속화되는지 확인합니다.

이렇게 나눈 이유는 실패 원인을 빠르게 찾기 위해서입니다. Controller 테스트에서 실패하면 API 매핑 문제이고, Service 테스트에서 실패하면 Use Case 문제이며, Domain 테스트에서 실패하면 상태 전이 규칙 문제입니다.

# 13. 현재 구현의 한계

현재 구현은 Operation Job Engine의 MVP입니다.

아직 남아 있는 부분은 다음과 같습니다.

- Worker 동시 Claim 제어
- FOR UPDATE SKIP LOCKED 적용
- Lease 만료 Job 복구
- 실제 백업 실행
- Audit 조회 API
- RBAC

특히 Worker Claim은 현재 JPA 조회 기반이므로 여러 Worker가 동시에 Job을 가져가는 상황에서는 보완이 필요합니다. 이후에는 MySQL Native Query를 사용해 FOR UPDATE SKIP LOCKED를 적용할 계획입니다.