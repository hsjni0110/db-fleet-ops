Operation Job과 Operation Task를 연결한 Agent 기반 백업 실행 구조

# 1. 이전 단계에서 해결한 문제

이전 단계에서는 Control Plane과 Go Agent를 분리했습니다.

처음에는 Spring Boot 서버 안에서 모든 작업을 직접 수행하는 구조도 생각할 수 있었습니다. 예를 들어 백업 요청이 들어오면 Control Plane이 직접 DB에 접속해서 mysqldump를 실행하는 방식입니다. 단순한 구조만 놓고 보면 이 방식이 더 쉬워 보입니다.

하지만 실제 운영 환경에서는 DB 서버가 Control Plane과 같은 네트워크에 있지 않을 수 있고, DB Host 내부에서만 접근 가능한 파일 시스템이나 로컬 소켓을 사용해야 할 수도 있습니다. 또한 DB 서버에 외부 inbound port를 여는 것도 부담이 됩니다.

그래서 구조를 다음과 같이 분리했습니다.

Spring Control Plane
  - DB Inventory 관리
  - Operation Job 관리
  - Agent 등록/Heartbeat 관리
  - Task 상태 관리

Go Agent
  - DB Host 내부에 설치
  - Control Plane으로 outbound 요청
  - Linux 상태 수집
  - 실제 Host 작업 실행

이 구조의 핵심은 Control Plane이 작업을 직접 수행하지 않고, DB Host에 설치된 Agent가 실제 작업을 수행한다는 점입니다.

- Control Plane → 작업 지시 저장
- Go Agent → 주기적으로 작업 조회
- Go Agent → Host 내부에서 작업 실행
- Go Agent → 결과 보고


이전 단계에서는 이 구조의 기본 골격을 만들었습니다. Agent 등록, Heartbeat, Task Polling, Task Start/Complete/Fail 흐름을 구현했습니다.

하지만 아직 중요한 문제가 남아 있었습니다.

Agent는 Task를 가져갈 수 있었지만, 그 Task가 상위 운영 요청인 Operation Job과 명확하게 연결되어 있지 않았습니다.

즉, 다음 두 흐름이 따로 존재했습니다.

Operation Job
  - 백업 요청 생성
  - Worker Claim
  - Retry
  - Audit

Agent Task
  - Agent가 가져가는 작업
  - Start
  - Complete
  - Fail

이 둘이 분리되어 있으면 사용자는 백업 Job을 생성했지만, 실제 Agent 작업이 어떻게 연결되는지 추적하기 어렵습니다.

그래서 이번 단계에서는 Operation Job과 Agent 실행 작업을 하나의 흐름으로 연결했습니다.

# 2. 이번 단계에서 해결하려는 문제

이번 단계의 목표는 단순합니다.

사용자가 백업 Job을 요청하면
실제 Go Agent가 mysqldump를 실행하고
그 결과가 다시 Operation Job의 성공/실패로 반영되어야 합니다.

이를 위해 필요한 흐름은 다음과 같습니다.

```
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
OperationJob 성공 처리
```

이 흐름이 완성되어야 사용자는 단순히 "Task가 성공했다"가 아니라 "백업 운영 요청이 성공했다"는 결과를 볼 수 있습니다.

운영 플랫폼에서 중요한 것은 개별 실행 단위보다 상위 요청의 상태입니다.

예를 들어 사용자는 다음과 같이 생각합니다.

- 1번 DB 백업이 성공했는가?
- 실패했다면 왜 실패했는가?
- 재시도 가능한가?
- 언제 시작했고 언제 끝났는가?
- 어떤 Agent가 실행했는가?
- 결과 파일은 검증되었는가?

따라서 내부적으로 Agent가 Task를 실행하더라도, 최종적으로는 OperationJob 상태에 반영되어야 합니다.


# 3. AgentTask가 아니라 OperationTask로 변경한 이유

초기에는 Agent가 가져가는 작업이라는 의미로 AgentTask라는 이름을 사용했습니다.

처음 구현할 때는 이 이름이 자연스러워 보였습니다.

```
Agent가 가져가는 작업
  ↓
AgentTask
```

하지만 기능을 확장하면서 이 이름이 정확하지 않다는 점이 드러났습니다.

백업, 복원, 설정 변경, Linux 상태 수집 같은 작업은 Agent가 실행할 수 있습니다. 하지만 이 작업들의 본질은 Agent 자체의 속성이 아닙니다.

Agent의 본질은 다음에 가깝습니다.

- Agent ID
- Agent Token
- Agent Name
- Hostname
- IP Address
- Status
- Last Heartbeat

반면 Task의 본질은 다음입니다.

- 작업 종류
- 작업 상태
- 작업 입력값
- 작업 결과
- 실행 Agent
- 상위 OperationJob
- 시작 시간
- 완료 시간
- 실패 코드
- 실패 메시지

즉, Task는 Agent의 생명주기보다 Operation의 생명주기에 더 가깝습니다.

백업을 예로 들면 더 명확합니다.

```
Backup OperationJob
  ↓
MYSQL_LOGICAL_BACKUP OperationTask
  ↓
Go Agent 실행
```

Agent는 실행자입니다.

Task는 운영 작업의 하위 실행 단위입니다.

그래서 AgentTask를 OperationTask로 변경했습니다.

최종 개념은 다음과 같습니다.

```
OperationJob
  └── OperationTask
        └── Agent가 실행
```

이렇게 변경하면 앞으로 복원 검증, 백업 업로드, 설정 점검 같은 단계도 자연스럽게 확장할 수 있습니다.

예를 들어 하나의 백업 Job이 여러 Task로 나뉠 수도 있습니다.

```
OperationJob: BACKUP
  ├── OperationTask: MYSQL_LOGICAL_BACKUP
  ├── OperationTask: BACKUP_ARTIFACT_VERIFY
  ├── OperationTask: BACKUP_UPLOAD
  └── OperationTask: RESTORE_VERIFY
```

현재는 하나의 Backup Job이 하나의 MYSQL_LOGICAL_BACKUP Task를 생성하지만, 구조적으로는 여러 단계로 확장할 수 있는 형태를 선택했습니다.


# 4. 모듈 의존 방향을 다시 정리한 이유

AgentTask를 agent 모듈에 두면 처음에는 구조가 단순해 보입니다.

```
agent
  ├── Agent
  ├── AgentTask
  └── AgentTaskService
```

하지만 OperationJob과 연결하려고 하면 문제가 생깁니다.

OperationWorkerService는 Backup Job을 Claim한 뒤 Task를 생성해야 합니다.

```
OperationWorkerService
  ↓
AgentTaskService
```

그리고 AgentTaskService는 Task가 완료되면 OperationJob도 성공 처리해야 합니다.

```
AgentTaskService
  ↓
OperationJobRepository
```

이렇게 되면 의존 방향이 꼬입니다.

```
operation → agent
agent → operation
```

이 구조는 순환 참조로 이어질 가능성이 큽니다.

당장은 같은 Spring Boot 프로젝트 안에 있어서 문제가 작아 보일 수 있지만, 나중에 모듈을 분리하거나 패키지를 명확히 관리할 때 문제가 됩니다.

그래서 Task를 operation 모듈로 이동했습니다.

현재 구조는 다음과 같습니다.

```
operation
  ├── OperationJob
  ├── OperationTask
  ├── OperationWorkerService
  └── OperationTaskService

agent
  ├── Agent
  ├── AgentRegistrationService
  └── AgentHeartbeatService
```

의존 방향은 다음과 같습니다.

```
operation → agent
```

operation 모듈은 "어떤 Agent에게 작업을 배정할지" 결정해야 하므로 agent 정보를 조회할 수 있습니다.

반대로 agent 모듈은 operation을 알 필요가 없습니다.

Agent는 자신의 등록 상태와 Heartbeat만 관리합니다. 실제 작업의 의미와 결과 처리는 operation 모듈이 담당합니다.

이렇게 하면 책임이 더 명확해집니다.

- agent 모듈
  - Agent 자체 관리

- operation 모듈
  - 운영 작업 관리
  - 작업 실행 단위 관리
  - Job과 Task의 상태 연결

# 5. 현재 도메인 구조

현재 주 도메인은 다음과 같이 나뉩니다.

```
agent.domain
  ├── Agent
  ├── AgentStatus
  └── AgentHostMetric
operation.domain
  ├── OperationJob
  ├── JobStatus
  ├── JobType
  ├── OperationTask
  ├── OperationTaskStatus
  └── OperationTaskType
```

Agent는 실행 주체입니다.

```
Agent
  - id
  - agentName
  - hostname
  - ipAddress
  - osName
  - architecture
  - agentVersion
  - agentToken
  - status
  - lastHeartbeatAt
```

OperationJob은 사용자가 요청한 상위 운영 작업입니다.

```
OperationJob
  - id
  - jobType
  - status
  - targetDatabaseId
  - requestedBy
  - idempotencyKey
  - retryCount
  - maxRetryCount
  - leaseOwner
  - leaseUntil
  - availableAt
  - startedAt
  - finishedAt
  - resultCode
  - resultMessage
```

OperationTask는 실제 실행 단위입니다.

```
OperationTask
  - id
  - agentId
  - operationJobId
  - taskType
  - status
  - parametersJson
  - resultPayloadJson
  - errorCode
  - errorMessage
  - startedAt
  - completedAt
  - createdAt
  - updatedAt
```

여기서 중요한 필드는 `operationJobId`입니다.

이 값이 있으면 해당 `Task`는 특정 `OperationJob`의 하위 실행 단위입니다.

```
OperationJob.id = 100
  ↓
OperationTask.operationJobId = 100
```

Task가 성공하면 연결된 Job도 성공 처리할 수 있고, Task가 실패하면 연결된 Job도 실패 처리할 수 있습니다.


# 6. Backup Job 생성 흐름

백업 작업은 사용자가 API를 호출하면서 시작됩니다.

```
POST /api/v1/database-instances/{databaseId}/operations/backups
```

요청 예시는 다음과 같습니다.

```json
{
  "reason": "manual backup before deployment",
  "requestedBy": "local-user"
}
```

이 요청이 들어오면 Control Plane은 실제 백업을 바로 실행하지 않습니다.

대신 OperationJob을 생성합니다.

```
OperationJob
  - jobType: BACKUP
  - status: QUEUED
  - targetDatabaseId: 요청 대상 DB ID
  - requestedBy: 요청자
  - retryCount: 0
  - maxRetryCount: 3
```

여기서 실제 작업을 즉시 실행하지 않는 이유는 백업이 오래 걸리거나 실패할 수 있는 작업이기 때문입니다.

HTTP 요청 안에서 백업을 직접 실행하면 다음 문제가 생깁니다.

- 요청 시간이 길어짐
- HTTP timeout 가능성 증가
- 실패 시 Retry 처리 어려움
- 작업 진행 상태 추적 어려움
- 동일 요청 중복 처리 위험

그래서 백업 요청은 Job으로 저장하고, 별도 Worker가 가져가 처리하도록 했습니다.

```
API 요청
  ↓
OperationJob 저장
  ↓
즉시 응답
  ↓
Worker가 비동기 처리
```

이 구조 덕분에 사용자는 백업 요청을 빠르게 접수할 수 있고, 이후 Job 조회 API로 상태를 확인할 수 있습니다.


# 7. Idempotency-Key를 둔 이유

백업 생성 API에는 Idempotency-Key를 사용합니다.

> Idempotency-Key: idem-backup-001

이 값은 같은 요청이 중복으로 들어왔을 때 같은 Job을 반환하기 위한 키입니다.

예를 들어 사용자가 백업 버튼을 눌렀는데 네트워크가 불안정해서 클라이언트가 재시도할 수 있습니다.

```
첫 번째 요청
  ↓
서버는 Job 생성
  ↓
응답 전 네트워크 끊김
  ↓
클라이언트는 실패로 판단
  ↓
같은 요청 재전송
```

이때 Idempotency-Key가 없으면 백업 Job이 두 개 생성될 수 있습니다.

```
Job 1: BACKUP
Job 2: BACKUP
```

이러면 실제 백업도 중복 실행될 수 있습니다.

그래서 현재 구조에서는 다음 조합을 기준으로 중복을 판단합니다.

```
databaseId
jobType
idempotencyKey
```

이미 같은 조합의 Job이 있으면 새로 생성하지 않고 기존 Job을 반환합니다.

이 방식은 운영 작업에서 매우 중요합니다. 특히 백업, 복원, 설정 변경처럼 한 번 실행하면 비용이 크거나 부작용이 있는 작업은 중복 실행을 막아야 합니다.

# 8. Worker Claim과 Lease

Job은 생성만으로 실행되지 않습니다.

Worker가 다음 API를 호출해 실행할 Job을 가져갑니다.

```
POST /internal/v1/workers/{workerId}/jobs/claim
```

Worker가 Job을 가져가면 Job 상태는 다음과 같이 바뀝니다.

```
QUEUED
  ↓
RUNNING
```

그리고 leaseOwner, leaseUntil이 설정됩니다.

```
leaseOwner = worker-1
leaseUntil = now + 60 seconds
```

Lease는 "이 Job은 일정 시간 동안 이 Worker가 처리 중이다"라는 표시입니다.

이 값이 필요한 이유는 Worker 장애 때문입니다.

예를 들어 Worker가 Job을 가져간 뒤 프로세스가 죽을 수 있습니다.

```
Worker Claim
  ↓
Job RUNNING
  ↓
Worker process crash
  ↓
Job이 영원히 RUNNING 상태로 남을 위험
```

이 문제를 해결하려면 Lease 만료 시점 이후에는 다른 Worker가 다시 가져갈 수 있어야 합니다.

현재 구현은 기본 Claim과 Lease 설정까지만 구현했고, 만료된 Lease를 다시 회수하는 정교한 로직은 이후 개선 지점입니다.

# 9. Backup Job Claim 시 OperationTask 생성

이번 단계에서 가장 중요한 변화는 Backup Job을 Claim할 때 OperationTask를 생성한다는 점입니다.

흐름은 다음과 같습니다.

```
Worker가 BACKUP Job Claim
  ↓
OperationJob 상태 RUNNING
  ↓
ONLINE Agent 조회
  ↓
MYSQL_LOGICAL_BACKUP OperationTask 생성
```

Task 예시는 다음과 같습니다.

```
OperationTask
  - agentId: 1
  - operationJobId: 100
  - taskType: MYSQL_LOGICAL_BACKUP
  - status: QUEUED
  - parametersJson: {...}
```

현재 Agent 선택 방식은 MVP 기준으로 단순합니다.

> 가장 최근 Heartbeat가 들어온 ONLINE Agent 선택

이 방식은 아직 완벽하지 않습니다.

왜냐하면 특정 DB가 어느 Host에 있는지, 어떤 Agent가 해당 DB에 접근 가능한지 연결 정보가 없기 때문입니다.

향후에는 다음 구조가 필요합니다.

```
ManagedDatabase
  - id
  - host
  - port
  - dbmsType
  - assignedAgentId
```

또는 별도 매핑 테이블을 둘 수도 있습니다.

```
managed_database_agent_mapping
  - databaseId
  - agentId
```

하지만 현재 단계에서는 Agent 기반 실행 흐름을 먼저 완성하는 것이 목표이므로, ONLINE Agent 중 하나를 선택하는 방식으로 구현했습니다.

# 10. OperationTask의 상태 전이

OperationTask는 Agent가 실제로 실행할 작업입니다.

상태는 다음과 같이 전이됩니다.

```
QUEUED
  ↓
RUNNING
  ↓
SUCCEEDED
```

실패 시에는 다음과 같습니다.

```
QUEUED
  ↓
RUNNING
  ↓
FAILED
```

각 상태의 의미는 다음과 같습니다.

- QUEUED: Agent가 아직 가져가지 않은 상태
- RUNNING: Agent가 작업 시작을 보고한 상태
- SUCCEEDED: Agent가 작업 성공을 보고한 상태
- FAILED: Agent가 작업 실패를 보고한 상태
- CANCELLED: 작업이 취소된 상태

Task가 QUEUED 상태일 때 Go Agent가 Polling API로 가져갑니다.

```
GET /internal/v1/agents/{agentId}/tasks/next?agentToken={agentToken}
```

Task를 가져온 Agent는 먼저 Start API를 호출합니다.

```
POST /internal/v1/agents/{agentId}/tasks/{taskId}/start
```

이후 실제 작업을 실행하고, 성공하면 Complete API를 호출합니다.

```
POST /internal/v1/agents/{agentId}/tasks/{taskId}/complete
```

실패하면 Fail API를 호출합니다.

```
POST /internal/v1/agents/{agentId}/tasks/{taskId}/fail
```

이 구조를 통해 Control Plane은 Agent가 어떤 작업을 가져갔는지, 언제 시작했는지, 어떤 결과로 끝났는지를 추적할 수 있습니다.

# 11. Go Agent Runtime Loop

Go Agent는 한 번 실행하고 끝나는 CLI 프로그램이 아니라 계속 살아 있는 Runtime 프로세스입니다.

현재 Runtime Loop는 다음 흐름으로 구성됩니다.

```
Agent 시작
  ↓
설정 로드
  ↓
Local State 로드
  ↓
등록 필요 시 Register
  ↓
Heartbeat 전송
  ↓
Task Polling
  ↓
Task 있으면 실행
  ↓
주기적으로 반복
```

코드 관점에서는 `AgentService.Run()`이 이 흐름을 담당합니다.

내부적으로는 두 개의 ticker를 둡니다.

```
heartbeatTicker
pollTicker
```

Heartbeat 주기가 되면 Host 상태를 수집해 Control Plane으로 보냅니다.

Polling 주기가 되면 다음 Task를 조회합니다.

```
select
  - ctx.Done()
  - heartbeatTicker.C
  - pollTicker.C
```

여기서 context.Context를 사용하는 이유는 종료 신호를 안전하게 처리하기 위해서입니다.

예를 들어 Agent 프로세스에 Ctrl + C가 들어오거나 Kubernetes가 Pod 종료를 위해 SIGTERM을 보내면 context가 취소됩니다.

```
SIGTERM
  ↓
context cancel
  ↓
Run loop 종료
  ↓
Agent 정상 종료
```

이 구조를 사용하면 나중에 백업 실행 중 정리 작업, 임시 파일 삭제, 현재 작업 상태 보고 같은 graceful shutdown 로직을 추가할 수 있습니다.


# 12. Agent Local State

Agent는 최초 실행 시 Control Plane에 등록합니다.

등록 결과로 다음 값을 받습니다.

```json
{
  "agentId": 1,
  "agentToken": "agent-token-..."
}
```

문제는 Agent가 재시작될 때입니다.

만약 재시작할 때마다 새로 등록하면 같은 Host가 여러 Agent로 중복 등록될 수 있습니다.

```
첫 실행 → Agent 1
재시작 → Agent 2
재시작 → Agent 3
```

이렇게 되면 Control Plane에서 Agent 상태를 신뢰하기 어려워집니다.

그래서 Go Agent는 등록 결과를 Local State 파일에 저장합니다.

```json
{
  "agentId": 1,
  "agentToken": "agent-token-..."
}
```

재실행 시에는 이 파일을 먼저 읽습니다.

```
Local State 있음
  ↓
기존 agentId / agentToken 사용
  ↓
Register 생략
```

Local State가 없거나 비어 있으면 Register API를 호출합니다.

```
Local State 없음
  ↓
Register API 호출
  ↓
agentId / agentToken 저장
```

현재 기본 파일 경로는 개발 편의를 위해 다음과 같이 두었습니다.

```
./agent-state.json
```

운영 환경에서는 다음과 같은 경로가 더 적절합니다.

```
/var/lib/db-fleet-agent/agent-state.json
```

이 파일은 Agent의 신원 정보이므로 권한도 중요합니다. 현재 파일 저장 시 디렉터리는 0700, 파일은 0600 권한을 사용하도록 설계했습니다.

# 13. Linux Host Metric 수집

Agent는 Heartbeat와 별도로 Linux 상태 수집 Task도 수행할 수 있습니다.

현재 수집 항목은 다음과 같습니다.

- CPU 사용률
- Memory 사용률
- Disk 사용률

CPU 사용률은 `/proc/stat`을 기준으로 계산합니다.

중요한 점은 `/proc/stat`의 값은 순간 사용률이 아니라 누적 tick 값이라는 것입니다.

따라서 한 번 읽어서는 CPU 사용률을 알 수 없습니다.

현재 구현은 다음과 같은 방식입니다.

```
첫 번째 /proc/stat 읽기
  ↓
200ms 대기
  ↓
두 번째 /proc/stat 읽기
  ↓
totalDelta 계산
  ↓
idleDelta 계산
  ↓
CPU 사용률 계산
```

Memory 사용률은 `/proc/meminfo`에서 `MemTotal`, `MemAvailable`을 사용합니다.

단순히 MemFree만 사용하지 않는 이유는 Linux가 `page cache`를 적극적으로 사용하기 때문입니다. 실제 사용 가능한 메모리를 판단하려면 MemAvailable이 더 적절합니다.

Disk 사용률은 `statfs("/")`를 사용해 root filesystem 기준으로 계산합니다.

```
total blocks
available blocks
used blocks
```

macOS 같은 개발 환경에는 `/proc/stat`, `/proc/meminfo`가 없습니다. 그래서 Linux가 아닌 OS에서는 metric 값을 0.0으로 반환하도록 했습니다.

이렇게 한 이유는 로컬 개발환경에서 테스트가 깨지는 것을 막기 위해서입니다.

# 14. AgentHostMetric 저장

Linux 상태 수집 Task가 완료되면 Agent는 결과를 resultPayloadJson으로 보고합니다.

예시는 다음과 같습니다.

```json
{
  "cpuUsagePercent": 14.2,
  "memoryUsagePercent": 53.7,
  "diskUsagePercent": 61.4
}
```

Control Plane은 이 JSON을 파싱해 agent_host_metric 테이블에 저장합니다.

```
agent_host_metric
  - id
  - agentId
  - cpuUsagePercent
  - memoryUsagePercent
  - diskUsagePercent
  - collectedAt
```

처음에는 Agent 테이블에 최신 metric만 저장하는 방식도 생각할 수 있습니다.

```
agent.latestCpuUsagePercent
agent.latestMemoryUsagePercent
agent.latestDiskUsagePercent
```

하지만 운영 플랫폼에서는 현재값보다 이력이 중요합니다.

예를 들어 다음 질문에 답하려면 이력이 필요합니다.

- 백업 실패 직전에 CPU가 높았는가?
- 메모리 사용률이 계속 증가하고 있었는가?
- 디스크 사용률이 특정 시점부터 급격히 증가했는가?
- Agent 장애 전후 Host 상태가 어땠는가?

그래서 Host Metric은 Agent 테이블에 덮어쓰지 않고 별도 이력 테이블로 저장했습니다.

현재는 단순 저장까지만 구현했지만, 향후에는 다음 기능으로 확장할 수 있습니다.

- Agent별 최근 10개 metric 조회
- 시간 구간별 metric 조회
- CPU/Memory/Disk threshold 알림
- 백업 실패 시점의 Host 상태 분석

# 15. MySQL Logical Backup 실행

초기 MySQL Backup Handler는 Stub이었습니다.

즉, 실제 백업을 수행하지 않고 다음과 같은 결과만 반환했습니다.

```json
{
  "status": "CREATED",
  "backupFile": "/tmp/db-fleetops-backups/orders.sql"
}
```

하지만 이 상태로는 운영 플랫폼이라고 보기 어렵습니다.

이번 단계에서는 Go Agent가 실제 mysqldump를 실행하도록 변경했습니다.

Task Payload는 다음과 같은 값을 포함합니다.

```json
{
  "databaseName": "orders",
  "host": "127.0.0.1",
  "port": 3306,
  "username": "backup_user",
  "password": "secret",
  "backupType": "LOGICAL",
  "compression": true
}
```

Go Agent는 이 값을 파싱한 뒤 mysqldump를 실행합니다.

명령 구조는 다음과 같습니다.

```
mysqldump
  --defaults-extra-file={tempDefaultsFile}
  --single-transaction
  --quick
  --databases {databaseName}
```

여기서 `--single-transaction`을 사용하는 이유는 InnoDB 기준으로 일관된 스냅샷 백업을 얻기 위해서입니다.

`--quick`은 결과를 메모리에 한 번에 적재하지 않고 row 단위로 스트리밍하기 위한 옵션입니다.

큰 DB를 백업할 때 메모리 사용량을 줄이기 위해 필요합니다.

# 16. 비밀번호를 명령어 인자로 넘기지 않은 이유

백업 구현에서 가장 조심한 부분 중 하나는 DB 비밀번호 전달 방식입니다.

단순하게 구현하면 다음과 같이 할 수 있습니다.

```
mysqldump -h127.0.0.1 -P3306 -ubackup_user -psecret orders
```

하지만 이 방식은 위험합니다.

명령어 인자는 프로세스 목록이나 로그에 노출될 수 있습니다.

```
ps aux
  ↓
mysqldump -ubackup_user -psecret
```

그래서 임시 defaults file을 사용했습니다.

임시 파일 내용은 다음과 같습니다.

```
[client]
host=127.0.0.1
port=3306
user=backup_user
password=secret
```

그리고 명령어에는 파일 경로만 전달합니다.

```
mysqldump --defaults-extra-file=/tmp/db-fleetops-mysql-xxxx.cnf ...
```

작업이 끝나면 임시 파일은 삭제합니다.

```
create temp defaults file
  ↓
mysqldump 실행
  ↓
defer os.Remove(defaultsFile)
```

물론 이 방식도 최종적인 보안 구조는 아닙니다.

현재는 MVP 구현이며, 장기적으로는 Task Payload에 password를 직접 넣는 방식 자체를 개선해야 합니다.

향후 구조는 다음과 같이 가는 것이 좋습니다.

```
Task Payload
  - credentialRef
Agent
  - Secret Store에서 credential 조회
  - 또는 Host local credential 사용
```

---

# 17. 파일이 생성됐다고 백업이 성공한 것은 아니다

`mysqldump` 실행 후 `.sql` 파일이 생성되면 성공으로 처리하고 싶을 수 있습니다.

하지만 운영 관점에서는 위험합니다.

예를 들어 다음 상황이 있을 수 있습니다.

- 0 Byte 파일 생성
- mysqldump 중간 실패
- 파일 일부만 기록
- 디스크 공간 부족
- 파일은 있지만 dump 형식이 아님
- 전송 중 깨짐

따라서 백업 파일이 생성된 후 최소한의 검증이 필요합니다.

이번 단계에서는 백업 산출물 검증기를 추가했습니다.

```
mysqldump 실행
  ↓
백업 파일 존재 확인
  ↓
파일 크기 확인
  ↓
SHA-256 checksum 생성
  ↓
MySQL dump header 확인
  ↓
VERIFIED 반환
```

검증 결과는 다음과 같은 형태입니다.

```json
{
  "status": "VERIFIED",
  "backupFile": "/tmp/db-fleetops-backups/orders-20260706-173000.sql",
  "fileSizeBytes": 182731,
  "checksumSha256": "5f70bf18a086007016ddcafcdb2934c567b38b354b77bb636c4f8f15e3f3c8ab",
  "createdAt": "2026-07-06T17:30:00+09:00",
  "message": "backup artifact verified"
}
```

이제 Task는 단순히 CREATED가 아니라 VERIFIED 상태의 결과를 반환합니다.

# 18. BackupVerifier를 분리한 이유

처음에는 MySQLDumpRunner 안에서 모든 것을 처리할 수도 있었습니다.

- mysqldump 실행
- 파일 검증
- checksum 생성
- 결과 반환

하지만 이렇게 하면 Runner의 책임이 커집니다.

MySQLDumpRunner의 핵심 책임은 dump 실행입니다.

```
MySQLDumpRunner
  - mysqldump 실행
  - output file 생성
```

백업 파일 검증은 별도 책임입니다.

```
BackupVerifier
  - 파일 존재 확인
  - 파일 크기 확인
  - checksum 생성
  - dump header 확인
```

그래서 두 클래스를 분리했습니다.

```
MySQLDumpRunner
  ↓
BackupVerifier
```

이렇게 분리하면 앞으로 다음 단계를 추가하기 쉽습니다.

```
Dump
  ↓
Verify Artifact
  ↓
Compress
  ↓
Upload
  ↓
Restore Verify
```

운영 플랫폼은 시간이 지나면 작업 단계가 늘어납니다. 처음부터 책임을 나누어두면 이후 확장이 훨씬 자연스럽습니다.

# 19. OperationTask 결과를 OperationJob에 반영

Agent가 Task를 완료하면 Control Plane은 Task 상태를 SUCCEEDED로 변경합니다.

하지만 여기서 끝나면 안 됩니다.

Task가 상위 OperationJob에 연결되어 있다면 Job도 같이 성공 처리해야 합니다.

현재 흐름은 다음과 같습니다.

```
Agent Complete 요청
  ↓
OperationTask SUCCEEDED
  ↓
operationJobId 확인
  ↓
OperationJob 조회
  ↓
OperationJob SUCCEEDED
```

실패도 동일합니다.

```
Agent Fail 요청
  ↓
OperationTask FAILED
  ↓
operationJobId 확인
  ↓
OperationJob 조회
  ↓
OperationJob FAILED
```
이 구조 덕분에 사용자는 내부 Task를 직접 보지 않아도 Job 상태만으로 전체 운영 요청의 결과를 알 수 있습니다.

```
Backup Job 조회
  ↓
SUCCEEDED / FAILED 확인
```

운영 플랫폼에서는 이 점이 중요합니다.

내부 실행 단위는 여러 개로 나뉠 수 있지만, 사용자에게는 상위 운영 작업의 상태가 일관되게 보여야 합니다.

# 20. 현재 전체 실행 흐름

현재까지 구현된 전체 흐름은 다음과 같습니다.

```
사용자 백업 요청
  ↓
POST /api/v1/database-instances/{databaseId}/operations/backups
  ↓
OperationJob 생성
  - type: BACKUP
  - status: QUEUED
  ↓
Worker Claim
  ↓
OperationJob RUNNING
  ↓
OperationTask 생성
  - type: MYSQL_LOGICAL_BACKUP
  - status: QUEUED
  - operationJobId 연결
  ↓
Go Agent Polling
  ↓
OperationTask Start
  - status: RUNNING
  ↓
Go Agent mysqldump 실행
  ↓
백업 파일 생성
  ↓
BackupVerifier 검증
  ↓
OperationTask Complete
  - status: SUCCEEDED
  - resultPayloadJson: VERIFIED 결과
  ↓
OperationJob SUCCEEDED
```

실패 흐름은 다음과 같습니다.

```
Go Agent 작업 실행
  ↓
mysqldump 실패 또는 검증 실패
  ↓
OperationTask Fail
  - status: FAILED
  - errorCode
  - errorMessage
  ↓
OperationJob FAILED
```

# 21. 테스트에서 중요하게 본 부분

이번 단계에서 테스트는 단순히 메서드 호출 여부만 확인하지 않았습니다.

중요하게 본 지점은 다음입니다.

- Backup Job을 Claim하면 OperationTask가 생성되는가?
- 생성된 OperationTask가 올바른 operationJobId를 가지는가?
- OperationTask가 성공하면 OperationJob도 성공하는가?
- OperationTask가 실패하면 OperationJob도 실패하는가?
- Linux Status Task 완료 시 AgentHostMetric이 저장되는가?
- Go Agent가 기존 Local State를 재사용하는가?
- Runtime Loop가 context cancel 시 종료되는가?
- BackupVerifier가 빈 파일을 실패로 판단하는가?
- BackupVerifier가 dump header 없는 파일을 실패로 판단하는가?

특히 OperationJob과 OperationTask를 연결하는 테스트에서는 단순 Mock보다 상태 전이를 확인하는 것이 중요했습니다.

예를 들어 다음 흐름을 하나의 테스트에서 확인했습니다.

```
OperationJob BACKUP 생성
  ↓
Worker Claim
  ↓
OperationTask 생성
  ↓
Task Complete
  ↓
OperationJob SUCCEEDED
```

이 테스트가 있어야 “Job과 Task가 실제로 연결되었다”고 말할 수 있습니다.

# 22. 현재 구조의 한계

현재 구조는 아직 운영 수준으로는 부족한 점이 있습니다.

첫 번째 한계는 Agent 선택 방식입니다.

현재는 가장 최근 Heartbeat가 들어온 ONLINE Agent를 선택합니다.

```
findFirstByStatusOrderByLastHeartbeatAtDesc(ONLINE)
```

하지만 실제로는 특정 DB에 접근 가능한 Agent를 선택해야 합니다.

향후에는 다음 정보가 필요합니다.

```
ManagedDatabase.assignedAgentId
또는
database_agent_mapping
```

두 번째 한계는 Credential 전달 방식입니다.

현재 Task Payload 안에 DB 접속 정보가 들어갑니다.

```json
{
  "username": "backup_user",
  "password": "secret"
}
```

이 방식은 MVP에서는 동작하지만 장기적으로 안전하지 않습니다.

향후에는 다음 방식으로 바꿔야 합니다.

- Credential Reference
- Secret Store
- Agent Local Credential
- Vault 연동
- Kubernetes Secret 연동

세 번째 한계는 Task 중복 생성 방지입니다.

현재는 Worker가 Backup Job을 Claim할 때 OperationTask를 생성합니다. 이때 같은 Job에 대해 Task가 중복 생성되지 않도록 더 명확한 제약이 필요합니다.

예를 들어 다음 제약을 생각할 수 있습니다.

```
operationJobId + taskType unique
```

네 번째 한계는 OperationTask 실패와 OperationJob Retry의 연결입니다.

현재 Task가 실패하면 Job도 실패합니다.

하지만 Retryable한 실패인지 판단해서 Job을 다시 QUEUED로 돌리는 흐름은 아직 정교하지 않습니다.

향후에는 Agent Fail 요청에 다음 값이 포함될 수 있습니다.

```json
{
  "errorCode": "MYSQL_CONNECTION_TIMEOUT",
  "errorMessage": "connection timeout",
  "retryable": true
}
```

그리고 OperationJob이 Retry 정책에 따라 다시 대기열로 들어가야 합니다.

다섯 번째 한계는 Restore Verification이 없다는 점입니다.

현재는 백업 파일이 생성되었고 dump header가 있는지만 확인합니다.

하지만 진짜 백업 성공은 복원 가능성까지 확인해야 합니다.

```
Backup Success
  ≠
Restore Possible
```

향후에는 임시 DB에 복원해보는 검증이 필요합니다.