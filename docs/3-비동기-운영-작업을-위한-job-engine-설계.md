# 비동기 운영 작업을 위한 Job Engine 설계

## 1. 이 문서가 설명하는 것

이 문서는 DB FleetOps가 백업과 설정 점검·적용 같은 운영 작업을 왜 `OperationJob`과 `OperationTask`로 나누었는지 설명합니다. 구현 방법만 나열하지 않고 다음 질문에 답하는 것을 목적으로 합니다.

- 운영 요청을 왜 즉시 실행하지 않고 먼저 Job으로 저장하는가?
- Job과 Task는 무엇이 다르며, 왜 둘 다 필요한가?
- 상태, Claim, Lease, Retry는 어떤 실패를 해결하는가?
- 현재 설계는 어떤 상황에 효율적이며, 그 대가로 무엇이 복잡해지는가?
- 현재 구현이 보장하는 범위와 아직 보장하지 못하는 범위는 어디까지인가?

이 문서에서는 `용어 정리.md`와 `도메인 정리.md`의 용어를 사용합니다. 특히 Agent와 Worker를 같은 의미로 사용하지 않습니다.

- **Operation Job(Job)**: 사용자가 달성하려는 하나의 운영 목적과 전체 상태·결과
- **Operation Task(Task)**: Agent가 실행하는 구체적인 단위
- **Worker**: 실행 가능한 Job을 가져가고 작업 순서와 상태를 조정하는 Control Plane 구성 요소
- **Agent**: DB Host 가까이에서 허용된 Task를 실행하는 주체
- **관리 DB**: FleetOps에 등록되어 운영 작업의 대상이 되는 DB Instance

## 2. 해결하려는 문제

DB FleetOps의 운영 작업은 일반적인 조회와 성격이 다릅니다. DB 목록을 읽는 요청은 결과를 바로 반환할 수 있지만, 백업이나 설정 적용은 다음 특성을 가집니다.

- 실행 시간이 길 수 있습니다.
- Control Plane, Worker 또는 Agent가 실행 도중 중단될 수 있습니다.
- 관리 DB의 일시적인 상태 때문에 실패할 수 있습니다.
- 같은 요청이 중복 실행되면 백업 파일, 설정값 또는 DB 부하에 영향을 줄 수 있습니다.
- 운영자는 요청이 끝날 때까지 기다리는 것보다 현재 진행 상태와 최종 결과를 확인할 수 있어야 합니다.

운영 작업을 API 요청 안에서 바로 실행하면 API 연결이 작업의 Lifecycle을 대신하게 됩니다. 사용자의 연결이 끊겼을 때 작업을 계속해야 하는지, 이미 실행된 요청을 다시 실행해야 하는지, 누가 결과를 기록해야 하는지가 불분명해집니다.

Operation Job은 이 문제를 해결하기 위해 운영 요청 자체를 먼저 Metadata DB에 저장합니다. API 연결이 종료되어도 Job은 남아 있으며, Worker는 저장된 Job을 기준으로 실행을 이어 갑니다.

예를 들어 운영자가 퇴근 전에 백업을 요청했다고 가정해 보겠습니다. 백업이 20분 걸린다고 해서 Console을 20분 동안 열어 둘 필요는 없습니다. Console에는 먼저 생성된 Job과 `QUEUED` 상태가 보이고, 이후에는 `RUNNING`, `SUCCEEDED` 또는 `FAILED` 상태를 확인하면 됩니다. 중간에 browser를 닫았다가 다시 열어도 같은 Job을 조회할 수 있습니다.

### 2.1 즉시 실행과 Job 저장 방식의 차이

| 관점 | API 요청 안에서 즉시 실행 | Operation Job으로 저장 후 실행 |
|---|---|---|
| 요청 응답 | 실제 작업이 끝날 때까지 길어질 수 있습니다 | Job 접수 결과를 먼저 반환할 수 있습니다 |
| 실행 추적 | API Log에 의존하기 쉽습니다 | Job 상태와 결과로 추적합니다 |
| 중복 요청 | 같은 작업이 다시 실행될 수 있습니다 | Idempotency Key로 같은 요청을 찾을 수 있습니다 |
| 일시 실패 | 호출자가 다시 요청해야 하기 쉽습니다 | 같은 Job을 Retry할 수 있습니다 |
| 실행 주체 중단 | 진행 상황을 잃기 쉽습니다 | Lease 만료를 기준으로 복구 지점을 만들 수 있습니다 |
| 구현 복잡도 | 처음에는 단순합니다 | 상태 전이, Worker, 복구 정책이 필요합니다 |

따라서 Job 방식은 짧고 실패 가능성이 낮은 조회보다, 오래 걸리고 결과 추적이 중요한 운영 작업에 더 효율적입니다. 반대로 매우 짧은 작업까지 모두 Job으로 만들면 저장과 상태 조회가 불필요하게 늘어날 수 있습니다.

## 3. Job과 Task를 분리한 이유

Job과 Task는 크기만 다른 같은 작업이 아닙니다. 처음에는 둘 다 “작업”처럼 보여 헷갈리기 쉽지만, 두 모델이 답하는 질문은 분명히 다릅니다.

- Job은 “운영자가 무엇을 달성하려고 했는가?”에 답합니다.
- Task는 “Agent가 실제로 무엇을 실행해야 하는가?”에 답합니다.

예를 들어 “production 관리 DB를 백업하고 복원 가능한지 확인한다”는 하나의 Job입니다. 이를 달성하려면 Agent가 다음 Task를 순서대로 실행할 수 있습니다.

```text
BACKUP Job
  ├─ MYSQL_LOGICAL_BACKUP Task
  └─ MYSQL_RESTORE_VERIFY Task
```

첫 번째 Task가 성공했다고 해서 복원 검증까지 요청한 Job이 성공한 것은 아닙니다. 모든 필요한 Task가 성공하여 사용자의 운영 목적을 달성했을 때 Job이 성공합니다. 이 경계를 분리하면 Agent의 개별 실행 결과와 사용자가 보는 전체 결과를 혼동하지 않을 수 있습니다.

조금 더 구체적으로 보면 다음과 같습니다. 백업 파일은 정상적으로 만들어졌지만 임시 DB에 복원하는 과정에서 오류가 날 수 있습니다. 이 경우 `MYSQL_LOGICAL_BACKUP` Task는 `SUCCEEDED`이고 `MYSQL_RESTORE_VERIFY` Task는 `FAILED`입니다. 사용자가 요청한 목적은 “복원 가능한 백업 확인”이므로 최종 Job은 성공으로 볼 수 없습니다. Job과 Task를 나눈 덕분에 “파일 생성은 성공했지만 복원 검증은 실패했다”는 사실을 그대로 보여줄 수 있습니다.

### 3.1 분리의 장점

- 하나의 Job을 여러 Task로 나누어 실행 순서를 조정할 수 있습니다.
- Task별 Agent, Task Type, Parameters, Result Payload와 오류를 별도로 남길 수 있습니다.
- 백업은 성공했지만 복원 검증은 실패한 상황을 표현할 수 있습니다.
- Agent는 Job 전체 규칙을 알지 않고 자신에게 지정된 Task만 실행할 수 있습니다.
- Job 없이 Linux 상태 수집 같은 독립 Task도 현재 모델에서 표현할 수 있습니다.

### 3.2 분리의 단점

- Job 상태와 Task 상태가 서로 모순되지 않도록 Control Plane이 조정해야 합니다.
- Task 성공이 곧 Job 성공인지, 다음 Task 생성 조건인지 Job Type별 규칙이 필요합니다.
- Job과 Task가 각각 저장되므로 조회와 장애 분석 시 두 Lifecycle을 함께 확인해야 합니다.
- 독립 Task를 계속 허용할지, 모든 Task를 Job에 포함할지 Aggregate 경계를 추가로 확정해야 합니다.

이 복잡도는 단순 실행 기능에는 부담이지만, 백업 후 복원 검증처럼 여러 실행 단계를 하나의 운영 결과로 보여주어야 할 때 가치가 큽니다.

## 4. Job을 `QUEUED`로 먼저 저장하는 이유

Job은 생성과 동시에 실행되지 않고 `QUEUED` 상태로 저장됩니다.

```text
운영 요청
  → OperationJob 생성
  → QUEUED 저장
  → Worker Claim
  → RUNNING
  → SUCCEEDED | FAILED | TIMED_OUT
```

생성과 실행을 분리하면 “요청을 받았다”와 “실제로 수행했다”를 구분할 수 있습니다. Worker가 잠시 없더라도 요청은 사라지지 않고, Worker가 다시 동작하면 대기 중인 Job을 가져갈 수 있습니다.

예를 들어 운영자가 주말 백업 Job을 여러 개 요청했는데 Worker가 점검을 위해 잠시 중단되었다고 가정해 보겠습니다. Job을 먼저 저장해 두면 Worker가 다시 동작한 뒤 `QUEUED` Job을 순서대로 가져갈 수 있습니다. `QUEUED`는 아직 실행하지 않았다는 뜻이지, 요청을 잃어버렸다는 뜻은 아닙니다.

현재 Worker는 다음 조건과 순서로 Job을 찾습니다.

1. 상태가 `QUEUED`입니다.
2. `availableAt`이 현재 시각보다 이전입니다.
3. `priority`가 높은 Job을 먼저 봅니다.
4. 같은 우선순위에서는 먼저 생성된 Job을 먼저 봅니다.

이 방식은 긴 API 연결을 유지하는 것보다 요청량과 실행 속도가 다를 때 효율적입니다. 여러 요청이 한꺼번에 들어와도 먼저 Job으로 보관하고 Worker가 처리 가능한 속도로 가져갈 수 있기 때문입니다.

다만 Job이 많아지면 Metadata DB가 대기 목록의 역할까지 맡습니다. 따라서 조회 성능, 상태별 보관 기간, 완료 Job 정리 정책이 필요해집니다.

## 5. 상태를 도메인 규칙으로 관리한 이유

Job 상태는 단순한 화면 표시값이 아닙니다. 어떤 행위를 지금 허용할지를 결정하는 도메인 규칙입니다.

| 상태 | 의미 | 허용되는 대표 행위 |
|---|---|---|
| `QUEUED` | Worker가 가져갈 수 있는 실행 대기 상태 | `start(...)` |
| `RUNNING` | Worker가 Job의 순서와 상태를 조정 중인 상태 | `succeed(...)`, `fail(...)`, `timeout()` |
| `SUCCEEDED` | 운영 목적을 정상적으로 달성한 상태 | 현재 추가 상태 전이 없음 |
| `FAILED` | 운영 목적을 달성하지 못한 상태 | `retry(...)`, `cancel()` |
| `CANCELLED` | 더 이상 실행하지 않도록 취소한 상태 | 현재 추가 상태 전이 없음 |
| `TIMED_OUT` | 허용된 실행 시간을 넘긴 상태 | 현재 `cancel()` 가능 |

현재 주요 상태 전이는 다음과 같습니다.

```text
QUEUED → RUNNING → SUCCEEDED
                 → FAILED → QUEUED
                 → TIMED_OUT

QUEUED | RUNNING | FAILED | TIMED_OUT → CANCELLED
```

상태 전이를 `OperationJob`의 행위로 둔 이유는 Service마다 서로 다른 규칙으로 상태를 변경하지 못하게 하려는 것입니다. 예를 들어 `QUEUED` Job을 바로 성공 처리하거나 `SUCCEEDED` Job을 다시 시작하는 요청은 Aggregate Root가 거부합니다.

가령 아직 어떤 Worker도 가져가지 않은 `QUEUED` Job에 성공 결과가 기록된다면, 실제 작업을 누가 수행했는지 설명할 수 없습니다. 반대로 이미 `SUCCEEDED`인 백업 Job을 다시 시작하면 같은 백업이 두 번 실행될 수 있습니다. 상태 전이는 이런 앞뒤가 맞지 않는 요청을 초기에 막아 줍니다.

이렇게 두면 허용된 Lifecycle을 코드와 도메인 테스트 한곳에서 확인할 수 있습니다. 다만 새로운 상태나 전이를 추가할 때는 Job을 사용하는 모든 흐름의 의미를 다시 살펴봐야 합니다. 현재 `FAILED`와 `TIMED_OUT` Job의 취소 허용 여부처럼 정책 합의가 더 필요한 전이도 남아 있습니다.

## 6. Idempotency Key가 필요한 이유

사용자는 응답이 늦으면 같은 버튼을 다시 누를 수 있고, API를 호출하는 다른 시스템도 응답을 받지 못하면 같은 요청을 다시 보낼 수 있습니다. 첫 번째 요청이 실패한 것처럼 보여도 실제로는 Job 저장까지 끝났을 수 있습니다.

이때 새 Job을 무조건 만들면 같은 관리 DB에 같은 운영 작업이 여러 번 실행될 수 있습니다. 이를 막기 위해 다음 조합으로 기존 Job을 찾습니다.

```text
targetDatabaseId + jobType + idempotencyKey
```

같은 조합이 있으면 새 Job을 만들지 않고 기존 Job을 반환합니다. `idempotencyKey`는 Job ID가 아닙니다. Job ID는 생성된 개체를 구분하고, Idempotency Key는 여러 요청이 같은 운영 의도였는지를 나타냅니다.

예를 들어 운영자가 백업 버튼을 눌렀지만 화면이 멈춘 것처럼 보여 한 번 더 눌렀다고 가정해 보겠습니다. 두 요청이 모두 `backup-20260805-orders`라는 같은 Idempotency Key를 사용하면 두 번째 요청은 새 Job을 만들지 않고 첫 번째 Job을 돌려줍니다. 반대로 두 번째 요청에 다른 Key를 사용하면 FleetOps는 새로운 운영 의도로 판단합니다.

### 6.1 어디에 효율적인가

- 사용자의 중복 클릭
- 응답 유실 뒤 같은 API 요청 재전송
- 설정 적용이나 재시작처럼 중복 실행의 영향이 큰 Job

### 6.2 장점과 단점

이 덕분에 호출자는 응답을 받지 못했을 때 같은 요청을 비교적 안전하게 다시 보낼 수 있습니다. Service에서 기존 Job을 찾고 Metadata DB의 고유 제약으로 동시에 들어온 요청도 한 번 더 막습니다.

물론 Key만 넣는다고 저절로 안전해지는 것은 아닙니다. Key를 만드는 쪽에서 생성과 재사용 범위를 올바르게 관리해야 합니다. 서로 다른 운영 의도에 같은 Key를 사용하면 기존 Job이 반환되고, 같은 운영 의도에 매번 다른 Key를 사용하면 중복을 막지 못합니다. 현재 Key가 없는 요청도 허용하므로 모든 요청의 중복 방지를 보장하지는 않습니다.

## 7. Claim이 필요한 이유

Claim은 Worker가 실행 가능한 Job 하나의 소유권을 얻는 행위입니다. Job을 Metadata DB에서 삭제하는 것이 아니라 `QUEUED`에서 `RUNNING`으로 전환하고 다음 값을 기록합니다.

- `leaseOwner`: Job을 Claim한 Worker 식별자
- `leaseUntil`: 해당 소유권이 유효한 시각

Claim을 별도 개념으로 둔 이유는 여러 Worker가 같은 Job을 동시에 조정하지 못하게 하려는 것입니다. Worker 수를 늘릴 수 있으려면 각 Job의 현재 담당자를 구분할 수 있어야 합니다.

예를 들어 `worker-1`과 `worker-2`가 같은 시각에 백업 Job을 발견했다고 가정해 보겠습니다. 둘 다 해당 Job을 자신의 일로 판단하면 같은 백업 Task를 두 개 만들 수 있습니다. Claim은 “이 Job은 지금 `worker-1`이 맡았습니다”라는 사실을 남겨 이런 중복 조정을 막기 위한 경계입니다.

### 7.1 현재 구현이 효율적인 범위

현재는 JPA Repository로 실행 가능한 Job을 조회한 뒤 첫 번째 Job에 `start(...)`를 호출합니다. Job에는 optimistic-lock version이 있어 같은 Job의 동시 변경을 감지할 수 있습니다.

이 방식은 Worker가 하나이거나 Claim 경쟁이 적은 초기 단계에서 다음 장점이 있습니다.

- 별도 Job Queue 없이 Metadata DB만으로 흐름을 검증할 수 있습니다.
- Job 조회, 상태 전이, Audit Log를 하나의 Transaction에서 다루기 쉽습니다.
- 운영에 필요한 구성 요소가 적어 개발과 테스트가 단순합니다.

하지만 여러 Worker가 동시에 같은 `QUEUED` Job을 조회하는 일을 조회 단계에서 막지는 못합니다. optimistic locking은 충돌을 감지하는 안전장치이지, 충돌 자체를 없애는 Claim 방식은 아닙니다. 경쟁이 많아지면 예외와 재조회가 늘 수 있습니다.

향후 여러 Worker가 지속적으로 경쟁하는 단계에서는 Claim 순간에만 짧게 잠금을 사용하고, 이미 다른 Worker가 가져간 Job은 기다리지 않고 다음 Job을 선택하는 방식이 필요합니다. 실제 Task 실행 시간 전체에 잠금을 유지하면 안 됩니다. 긴 잠금은 다른 Worker의 Claim과 Job 조회를 함께 지연시키기 때문입니다.

## 8. Lease를 둔 이유

Claim만 있고 Lease가 없다면 Worker가 중단된 뒤에도 Job은 계속 `RUNNING`으로 남습니다. 다른 Worker는 이미 실행 중인 Job으로 판단하므로 다시 가져갈 수 없습니다.

예를 들어 `worker-1`이 Job을 Claim한 직후 Control Plane이 재시작되었다고 가정해 보겠습니다. `leaseOwner`만 있고 만료 시각이 없다면 다른 Worker는 언제까지 기다려야 하는지 알 수 없습니다. `leaseUntil`이 있으면 적어도 “이 시각이 지났는데도 결과가 없다면 소유권을 다시 판단해야 한다”는 기준이 생깁니다.

Lease는 “이 Worker가 영원히 Job을 소유한다”가 아니라 “`leaseUntil`까지 한시적으로 소유한다”는 의미입니다.

```text
Worker Claim
  → leaseOwner = worker-1
  → leaseUntil = 현재 시각 + 60초
```

Lease를 사용하면 다음 복구 판단의 근거가 생깁니다.

```text
RUNNING Job
  → leaseUntil 경과
  → Worker 소유권 만료
  → TIMED_OUT 처리 또는 QUEUED 복귀 여부 판단
```

Lease가 있으면 Worker 중단이 영구적인 Job 정체로 이어지는 일을 피할 수 있습니다. 성공·실패 보고 시 `leaseOwner`와 Worker ID를 비교하면 다른 Worker가 결과를 덮어쓰는 일도 막을 수 있습니다.

대신 Lease 시간은 신중하게 정해야 합니다. 너무 짧으면 정상 작업 중에도 소유권이 만료되고, 너무 길면 Worker 중단을 늦게 발견합니다. 실행 시간이 긴 Job에는 Lease 연장 규칙도 필요합니다.

현재 구현은 Claim 시 Lease를 기록하고 성공·실패 처리에서 Worker 소유권을 확인합니다. 그러나 Lease 연장과 만료 Job 자동 복구는 아직 구현되지 않았습니다. 따라서 Lease 필드가 존재한다는 사실만으로 Worker 중단 복구가 완성된 것은 아닙니다.

## 9. Retry를 상태 전이로 표현한 이유

Retry는 새 Job을 만드는 행위가 아닙니다. 실패한 같은 Job을 다시 실행 대기 상태로 돌리는 행위입니다. 같은 운영 요청의 시도 횟수와 최종 결과를 하나의 Lifecycle에서 추적하기 위해 다음과 같이 전이합니다.

```text
RUNNING → FAILED → QUEUED
```

Retry 시 `OperationJob`은 다음 정보를 함께 정리합니다.

- `retryCount` 증가
- `availableAt` 변경
- `leaseOwner`, `leaseUntil` 제거
- `finishedAt` 제거

`availableAt`을 현재보다 뒤로 설정하면 실패 직후 같은 조건에서 즉시 다시 실행되는 일을 피할 수 있습니다. 현재 Worker 흐름은 30초 뒤를 사용하고 최대 재시도 횟수는 3회입니다.

### 9.1 Retry가 효율적인 실패

- 잠시 발생한 네트워크 문제
- 관리 DB의 순간적인 부하
- 잠시 뒤 해소될 가능성이 있는 외부 실행 오류

반대로 잘못된 Credential, 허용되지 않은 설정 Parameter, 존재하지 않는 관리 DB처럼 같은 입력으로 다시 실행해도 결과가 달라지지 않는 실패에는 Retry가 비효율적입니다.

예를 들어 관리 DB가 잠깐 재시작 중이라 연결하지 못했다면 30초 뒤에는 성공할 수 있으므로 Retry가 도움이 됩니다. 하지만 Credential의 비밀번호가 틀렸다면 30초를 기다려도 같은 오류가 납니다. 이 경우에는 Retry를 반복하기보다 Credential을 바로잡고 새로운 실행 여부를 판단하는 편이 낫습니다.

Retry를 사용하면 운영자가 일시 실패마다 새 Job을 만들 필요가 없고, 원래 요청과 모든 시도 횟수를 함께 볼 수 있습니다. 반면 실패를 재시도 가능한 것으로 잘못 분류하면 같은 오류를 반복하여 관리 DB와 Agent에 부하를 줄 수 있습니다.

현재 Worker는 실패 보고의 `retryable` 값과 남은 재시도 횟수를 함께 확인합니다. 설정 적용 실패는 자동 Retry하지 않고, 설정 점검 중 발생한 예외는 Retry 대상으로 처리하는 등 Job Type에 따라 판단이 다릅니다.

## 10. Job Type에 따라 실행 방식이 다른 이유

현재 모든 Job이 같은 방식으로 실행되지는 않습니다.

| Job Type | 현재 실행 방식 | 이유 |
|---|---|---|
| `BACKUP` | Worker가 `MYSQL_LOGICAL_BACKUP` Task를 만들고 Agent가 Poll하여 실행합니다 | DB Host 가까이에서 백업 도구와 파일을 다뤄야 합니다 |
| `CONFIGURATION_CHECK` | Worker가 관리 DB와 Configuration Profile을 사용해 점검을 조정합니다 | Desired State와 Actual State 비교 및 결과 저장을 Control Plane이 관리합니다 |
| `CONFIGURATION_APPLY` | Worker가 허용된 Parameter 적용과 결과 저장을 조정합니다 | 적용 전후 Snapshot과 항목별 결과를 함께 관리해야 합니다 |
| `RESTART` | Job Type은 존재하지만 완전한 실행 흐름은 아직 없습니다 | 안전한 실행 조건과 Agent Task 계약이 더 필요합니다 |

백업을 Task로 분리하면 Agent가 DB Host 가까이에서 실행하고, Control Plane은 비밀번호나 임의 명령 실행을 직접 맡지 않으면서 전체 결과를 조정할 수 있습니다. 특히 백업 뒤 복원 검증 Task를 추가하는 흐름에 적합합니다.

설정 점검·적용을 Worker가 직접 조정하면 Configuration Profile, Snapshot, Drift, Apply를 하나의 Transaction 흐름에서 다루기 쉽습니다. 초기 구현에는 효율적이지만, 작업 시간이 길어지거나 Host별 실행이 필요해지면 Worker가 실제 실행까지 담당하는 부담이 커집니다.

즉, 현행 설계는 “모든 Job은 반드시 Task 하나가 된다”는 규칙이 아닙니다. Job Type별로 Agent 실행이 필요한 부분만 Task로 나누고 있습니다. 이 유연성은 점진적 구현에는 장점이지만, Job Type마다 실행 구조가 달라 운영자가 상태를 이해하기 어려워질 수 있다는 단점이 있습니다.

## 11. Task Poll을 Claim과 구분한 이유

Worker의 Claim과 Agent의 Poll은 서로 다른 행위입니다.

- Claim: Worker가 Job의 한시적 소유권을 얻습니다.
- Poll: Agent가 자신에게 지정된 다음 `QUEUED` Task를 요청합니다.

백업 흐름에서는 Worker가 Job을 Claim한 뒤 Task를 생성합니다. Agent는 Job을 직접 가져가지 않고 자신의 ID에 해당하는 Task를 Poll합니다. 이 구분 덕분에 Agent는 전체 Job Lifecycle이나 Retry 정책을 알 필요 없이 Task Type과 Parameters에 따라 실행할 수 있습니다.

예를 들어 `agent-1`에는 production 관리 DB의 백업 Task가, `agent-2`에는 staging 관리 DB의 Linux 상태 수집 Task가 지정되어 있을 수 있습니다. 각 Agent는 자신에게 지정된 다음 Task만 Poll합니다. 다른 Agent의 Task나 Job 전체 순서를 직접 판단하지 않습니다.

이 구조에서는 Control Plane이 운영 순서와 결과 판정을 책임지고, Agent는 허용된 현장 실행에 집중할 수 있습니다. 다만 Job은 `RUNNING`인데 Agent가 Task를 Poll하지 않는 상황처럼 두 실행 영역 사이의 정체를 별도로 관찰해야 합니다.

## 12. Audit Log를 남기는 이유

Job의 현재 상태만으로는 그 상태에 도달한 이유를 설명하기 어렵습니다. 예를 들어 `QUEUED` 상태는 최초 대기일 수도 있고, 실패 후 Retry된 상태일 수도 있습니다.

운영자가 아침에 `QUEUED` Job 하나를 발견했다고 가정해 보겠습니다. 상태만 보면 밤새 Worker가 한 번도 가져가지 않은 것인지, 새벽에 두 번 실패한 뒤 Retry를 기다리는 것인지 알 수 없습니다. Audit Log에서 `JOB_CLAIMED`, `JOB_FAILED`, `JOB_RETRIED` 순서를 확인하면 현재 상태가 만들어진 과정을 이해할 수 있습니다.

Audit Log는 누가, 어떤 행위를, 어떤 대상에 수행했고 결과가 무엇이었는지를 남깁니다. 현재 Job 흐름에서는 생성, Claim, 성공, 실패, Retry, Claim 건너뜀, Task 생성 등의 중요 행위를 기록합니다.

Audit Log를 수정하지 않고 추가하는 방식으로 남기면 현재 상태와 별도로 시간 순서에 따른 운영 이력을 확인할 수 있습니다. 이는 장애 원인 분석과 운영 책임 확인에 효율적입니다.

대신 Job 처리마다 저장 작업이 추가되고 Audit 기록이 계속 늘어납니다. 보관 기간, 조회 성능, 민감 정보 제외 기준이 필요합니다. 특히 Request Payload, Result Payload, 오류 메시지를 Audit Log에 그대로 복사하면 Credential이나 불필요한 대용량 정보가 남을 수 있으므로 기록 범위를 제한해야 합니다.

## 13. Audit을 Port로 분리한 이유

Operation은 Audit Log가 어떻게 저장되는지보다 “이 행위를 기록해야 한다”는 계약만 알면 됩니다.

```text
Operation application
  → AuditRecorderPort
  → Audit application
  → AuditLog 저장
```

이 경계를 사용하면 Operation이 Audit의 Repository나 저장 방식에 직접 의존하지 않습니다. 테스트에서는 Port를 대체하여 Job 상태 전이와 Audit 요청을 각각 확인할 수 있습니다.

이렇게 나누면 두 컨텍스트의 책임이 섞이지 않고, Audit 저장 방식을 바꾸더라도 Operation이 받는 영향이 줄어듭니다. 대신 구현 클래스와 연결 설정이 추가되어 작은 흐름을 따라갈 때 살펴볼 파일이 늘어납니다. 운영 이력의 독립성이 중요한 FleetOps에서는 이 비용보다 컨텍스트 분리의 가치가 크다고 판단했습니다.

## 14. Metadata DB를 Job Queue로 사용한 이유

현재 Job은 별도 Job Queue가 아니라 Metadata DB에 저장됩니다. FleetOps가 이미 Job, Task, 관리 DB, Audit Log를 Metadata DB에 저장하므로 초기 단계에서 별도 구성 요소를 추가하지 않고 Lifecycle을 완성할 수 있기 때문입니다.

### 14.1 장점

- Job 상태와 운영 결과를 같은 저장 방식으로 조회할 수 있습니다.
- Transaction과 고유 제약을 이용해 생성과 상태 변경 규칙을 보호할 수 있습니다.
- 개발, 테스트, 배포에 필요한 구성 요소가 적습니다.
- 운영자는 Job ID를 기준으로 현재 상태와 결과를 일관되게 조회할 수 있습니다.

### 14.2 단점

- Job이 많아지면 운영 정보 조회와 Claim 조회가 같은 Metadata DB 자원을 사용합니다.
- 여러 Worker의 Claim 경쟁이 커질수록 잠금과 재시도 전략이 중요해집니다.
- 완료 Job과 Audit Log가 계속 쌓이므로 보관·정리 정책이 필요합니다.
- 별도 Job Queue가 제공하는 전달 보장과 소비자 조정 기능을 직접 설계해야 합니다.

따라서 이 선택은 Job 수와 Worker 수가 아직 크지 않고, 도메인 Lifecycle을 먼저 안정화해야 하는 단계에 효율적입니다. 처리량이 커지면 현재 장점보다 Claim 경쟁과 저장 부하가 커지는 시점을 측정하여 구조를 재검토해야 합니다.

## 15. 테스트를 책임별로 나눈 이유

테스트는 단순히 같은 동작을 여러 번 확인하는 것이 아니라, 실패 원인이 속한 책임을 빠르게 찾도록 나눕니다.

| 테스트 범위 | 확인하는 책임 |
|---|---|
| `OperationJobTest` | Job 생성과 상태 전이 불변식 |
| `OperationTaskTest` | Task 생성과 `QUEUED → RUNNING → SUCCEEDED|FAILED` 전이 |
| `OperationJobServiceTest` | 관리 DB 확인, Job 생성, Idempotency Key 처리 |
| `OperationWorkerServiceTest` | Claim, Worker 소유권, Job Type별 실행, 실패와 Retry |
| `OperationTaskServiceTest` | Agent 인증, Poll 이후 Task 시작·성공·실패, Job과 Task 결과 조정 |
| Controller 테스트 | API 경로, 요청과 응답 계약 |
| Persistence 테스트 | Job과 Task 저장, 상태 변경, optimistic locking |

도메인 테스트가 실패하면 상태 규칙을 먼저 확인하고, application 테스트가 실패하면 여러 Aggregate와 Port를 조정하는 흐름을 확인할 수 있습니다. Persistence 테스트는 코드상 성공한 전이가 실제 Metadata DB에도 같은 모습으로 저장되는지 확인합니다.

이렇게 테스트를 나누면 실패 원인을 좁히기 쉽고 도메인 규칙도 빠르게 확인할 수 있습니다. 대신 같은 흐름의 준비 코드가 여러 테스트에 나타날 수 있고, Job과 Task를 함께 사용하는 전체 흐름은 별도의 통합 테스트로 확인해야 합니다.

## 16. 설계 선택을 한눈에 보는 기준

| 설계 선택 | 해결하는 문제 | 특히 효율적인 상황 | 감수하는 비용 |
|---|---|---|---|
| Job을 먼저 저장 | 긴 API 요청과 실행 추적 | 백업, 설정 적용 | 상태 저장과 조회 필요 |
| Job과 Task 분리 | 운영 목적과 Agent 실행의 혼동 | 백업 후 복원 검증 | 두 Lifecycle 조정 필요 |
| 명시적 상태 전이 | 잘못된 순서의 상태 변경 | 실패·Retry가 있는 작업 | 전이 정책 변경 비용 |
| Idempotency Key | 같은 운영 요청의 중복 생성 | 재전송, 중복 클릭 | Key 관리 책임 |
| Claim과 Lease | Worker 간 소유권과 중단 복구 지점 | 여러 Worker 운영 | 만료·연장 정책 필요 |
| Retry와 `availableAt` | 일시 실패의 자동 재실행 | 네트워크·일시 부하 | 잘못된 Retry의 반복 부하 |
| Audit Port | Operation과 Audit 책임 분리 | 변경 이력이 중요한 운영 | 연결 구조와 저장 비용 |
| Metadata DB 기반 처리 | 초기 구성 단순화 | Worker와 Job 수가 적은 단계 | 규모 증가 시 Claim 경쟁 |

## 17. 현재 구현의 경계와 다음 결정

현재 구현은 Job 생성, 상태 전이, Idempotency Key, Worker Claim, Lease 기록, Worker 소유권 확인, Retry, Audit Log, 백업 Task와 복원 검증 Task 연결까지 제공합니다.

다음 항목은 아직 완성된 보장이 아닙니다.

- 여러 Worker가 동시에 Claim할 때 조회 단계에서 같은 Job 선택 방지
- Lease 연장
- 만료된 Lease를 가진 `RUNNING` Job의 자동 `TIMED_OUT` 처리 또는 재실행
- `CANCELLED` Task의 도메인 행위
- `RESTART` Job의 안전한 실행 흐름
- 완료 Job과 Audit Log의 보관·정리 기준
- 모든 Job Type에서 Job과 Task를 일관되게 나눌지에 대한 정책

다음 개선은 기능을 한꺼번에 늘리는 것보다 실제 운영에서 관찰되는 문제를 기준으로 선택해야 합니다.

- 같은 Job의 Claim 충돌이 관찰되면 Claim 동시성을 먼저 강화합니다.
- Worker 중단으로 `RUNNING` Job이 남으면 Lease 만료 복구를 먼저 완성합니다.
- Job 대기 시간이 길어지면 `priority`, `availableAt`, Worker 처리량을 함께 관찰합니다.
- Job과 Task 상태가 자주 어긋나면 Job Type별 완료 조건을 도메인 규칙으로 더 명확히 만듭니다.
- Metadata DB의 Claim 조회가 다른 기능에 영향을 주면 Job 저장과 실행 전달 구조를 재검토합니다.

이 설계의 핵심은 “비동기라서 빠르다”가 아닙니다. 운영 요청의 의도, 실행 주체, 진행 상태, 실패와 복구, 최종 결과를 API 연결과 분리하여 끝까지 추적할 수 있게 하는 데 있습니다.
