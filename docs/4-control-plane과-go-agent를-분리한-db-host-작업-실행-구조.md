Control Plane과 Go Agent를 분리한 DB Host 작업 실행 구조

# 1. 왜 Agent가 필요한가

DB FleetOps의 초기 기능은 Control Plane 중심으로 구현되었습니다. Spring Boot API 서버가 관리 대상 DB를 등록하고, Health Check와 Diagnostics API를 통해 DB 상태를 조회하는 구조였습니다. 이 방식은 DB에 직접 접속해 SQL을 실행하는 진단에는 적합합니다.

이제 단순 조회가 아니라 DB Host 내부의 Linux 상태를 수집하고, 백업 작업처럼 서버 내부 명령 실행이 필요한 기능을 다루기 시작합니다.

Control Plane이 직접 DB 서버에 접속해 명령을 실행하는 방식도 가능은 합니다. 하지만 이 방식은 보안과 네트워크 측면에서 부담이 큽니다. Control Plane이 각 DB Host로 접근하려면 SSH, Agent API Port, 방화벽, 인증서, 권한 관리가 필요합니다. 특히 여러 서버에 흩어진 DB를 관리하는 플랫폼이라면 서버마다 inbound 접근을 허용해야 하는 구조가 됩니다.

그래서 DB FleetOps는 DB Host 내부에서 실행되는 경량 Go Agent를 두는 방향으로 설계했습니다.

DB Host
 ├── MySQL
 ├── Linux OS
 └── DB FleetOps Go Agent

Agent는 Host 내부에서 Linux 상태를 수집하고, 허용된 작업만 수행한 뒤 결과를 Control Plane에 보고합니다. Control Plane은 작업을 지시하고 결과를 저장하는 역할을 맡고, Agent는 실제 Host 가까이에서 실행하는 역할을 맡습니다.

# 2. Agent 통신을 Pull 방식으로 설계한 이유

Agent와 Control Plane의 통신 방향은 다음과 같이 잡았습니다.

Go Agent → Control Plane

즉, Control Plane이 Agent에게 직접 접속하는 Push 방식이 아니라, Agent가 Control Plane으로 주기적으로 요청을 보내는 Pull 방식입니다.

이 방식을 선택한 가장 큰 이유는 DB 서버에 별도 inbound 포트를 열지 않기 위해서입니다. DB 서버는 일반적으로 외부 접근을 최소화해야 하는 자산입니다. Agent가 서버 포트를 열고 Control Plane의 요청을 기다리게 만들면 방화벽, 접근 제어, 인증서, 네트워크 라우팅 문제가 따라옵니다.

반대로 Agent가 Control Plane으로 나가는 outbound 요청만 사용하면 구조가 단순해집니다.

```
Agent 등록
  ↓
Heartbeat 전송
  ↓
Task Polling
  ↓
Task 실행
  ↓
결과 보고
```

이 구조는 운영 환경에서도 자연스럽습니다. 많은 서버가 중앙 제어 서버로 상태를 보고하고 작업을 가져가는 방식이기 때문입니다.

현재 구현은 진짜 Long Polling처럼 서버가 오래 대기하는 구조까지는 가지 않았습니다. 우선은 tasks/next API를 호출하면 즉시 Task 유무를 반환하는 방식입니다. 하지만 API 이름과 흐름은 Long Polling으로 확장할 수 있게 설계했습니다.

# 3. Java Control Plane의 Agent 모듈 구조

Java Control Plane에는 agent 모듈을 새로 추가했습니다.

```
src/main/java/com/dbfleetops/agent
├── api
├── application
├── domain
├── dto
├── infra
└── port
```

이 구조는 기존 database, health, operation 모듈과 같은 규칙을 따릅니다.

api는 HTTP 요청과 응답을 담당합니다. Agent 등록, Heartbeat, Task 조회, Task 완료/실패 API가 여기에 위치합니다.

application은 Use Case를 실행합니다. Agent 등록, Token 검증, Task 생성, Task 시작, 완료, 실패 처리 흐름을 담당합니다.

domain은 Agent와 AgentTask의 상태와 규칙을 표현합니다.

dto는 API 요청과 응답 모델입니다.

infra는 JPA Repository처럼 실제 저장 기술과 연결되는 구현입니다.

현재 port는 크게 사용하지 않았지만, 향후 Agent 작업 결과를 Operation Job으로 전파하거나 Event Publisher를 붙일 때 확장 지점으로 사용할 수 있습니다.

# 4. Agent 도메인을 먼저 만든 이유

Agent에는 다음 상태가 있습니다.

```
ONLINE
OFFLINE
UNKNOWN
DISABLED
```

Agent는 단순히 등록되어 있는지가 중요한 것이 아니라, 현재 살아 있는지, 운영자가 비활성화했는지, 아직 상태 판단이 어려운지를 구분해야 합니다.

ONLINE은 Heartbeat가 정상적으로 들어오는 상태입니다. OFFLINE은 일정 시간 이상 Heartbeat가 없는 상태로 판단할 수 있습니다. DISABLED는 운영자가 의도적으로 Agent를 사용하지 않도록 만든 상태입니다.

중요한 규칙은 DISABLED 상태입니다. 비활성화된 Agent가 Heartbeat를 보냈다고 해서 자동으로 다시 ONLINE이 되면 안 됩니다. 운영자가 비활성화한 의미가 사라지기 때문입니다. 그래서 Agent.heartbeat() 안에서 DISABLED 상태이면 예외를 발생시키도록 했습니다.

이 규칙을 Service가 아니라 Domain에 둔 이유는 상태 변경 규칙을 한 곳에 모으기 위해서입니다.

# 5. AgentTask와 OperationJob을 분리한 이유

이전에 이미 Operation Job을 만들었습니다. 그렇다면 AgentTask를 따로 만들 필요가 있는지 고민할 수 있습니다.

하지만, 둘은 역할이 다릅니다.

```
OperationJob
→ 플랫폼 관점의 운영 작업
AgentTask
→ 특정 Agent가 Host 내부에서 실행할 실제 작업
```

예를 들어 백업 요청이 들어오면 플랫폼 관점에서는 하나의 BACKUP Operation Job입니다. 하지만 실제 실행은 특정 DB Host에 설치된 Agent가 수행해야 합니다. 이때 Agent에게 전달되는 하위 작업이 MYSQL_LOGICAL_BACKUP AgentTask입니다.

```
OperationJob: BACKUP
  ↓
AgentTask: MYSQL_LOGICAL_BACKUP
  ↓
Go Agent 실행
```

현재는 OperationJob과 AgentTask를 완전히 연결하지 않았습니다. 먼저 AgentTask 단독 생성, Polling, 시작, 완료, 실패 흐름을 구현했습니다. 이렇게 분리한 이유는 한 번에 모든 것을 연결하면 문제 지점이 불명확해지기 때문입니다.

먼저 AgentTask 자체의 생명주기를 검증하고, 이후 Operation Job과 연결하는 편이 안전합니다.

# 6. AgentTask 상태 전이

AgentTask의 상태는 다음과 같습니다.

```
QUEUED
RUNNING
SUCCEEDED
FAILED
CANCELLED
```

기본 흐름은 다음과 같습니다.

```
QUEUED
  ↓ start
RUNNING
  ↓ complete
SUCCEEDED
```

실패 시에는 다음 흐름입니다.

```
QUEUED
  ↓ start
RUNNING
  ↓ fail
FAILED
```

AgentTask도 OperationJob처럼 상태 전이를 Domain 메서드로 관리했습니다.

```
start()
complete()
fail()
cancel()
```

이렇게 한 이유는 잘못된 상태 변경을 막기 위해서입니다. 예를 들어 아직 Agent가 시작하지 않은 Task가 완료 처리되면 안 됩니다. 이미 성공한 Task가 다시 실패 처리되는 것도 이상합니다.

상태 전이 규칙을 Domain에 두면 Service는 Use Case 흐름에 집중할 수 있고, 상태 무결성은 Domain이 책임질 수 있습니다.

# 7. Agent Token을 둔 이유

현재 구현에서는 Agent 등록 시 Control Plane이 agentToken을 발급합니다.

```
{
  "agentId": 1,
  "agentToken": "agent-token-...",
  "status": "ONLINE"
}
```

이후 Heartbeat와 Task API는 agentId와 agentToken을 함께 검증합니다.

```
agentId 조회
  ↓
agentToken 일치 여부 확인
  ↓
요청 처리
```

아직 mTLS나 정식 인증 체계를 붙이지 않았기 때문에 최소한의 식별 수단으로 Token을 둔 것입니다.

물론 현재 방식에는 한계가 있습니다. Token이 평문으로 저장되고, 요청 Body나 Query Parameter로 전달됩니다. 운영 수준으로 가려면 다음 개선이 필요합니다.

- Token 암호화 저장
- Authorization Header 사용
- mTLS 적용
- Token Rotation
- Agent별 권한 제한

하지만 MVP 단계에서는 Agent 식별과 소유권 검증 흐름을 먼저 만드는 것이 중요하다고 판단했습니다.

# 8. Task Polling API를 둔 이유

Agent가 작업을 가져가는 API는 다음과 같습니다.

```
GET /internal/v1/agents/{agentId}/tasks/next?agentToken={agentToken}
```

응답은 Task가 있을 때와 없을 때를 명확히 나눴습니다.

```json
{
  "hasTask": true,
  "taskId": 1,
  "taskType": "COLLECT_LINUX_STATUS",
  "parametersJson": "{}"
}
```

Task가 없으면 다음처럼 반환합니다.

```json
{
  "hasTask": false,
  "taskId": null,
  "taskType": null,
  "parametersJson": null
}
```

hasTask를 둔 이유는 Agent 쪽 분기 처리를 단순하게 만들기 위해서입니다. Task ID가 null인지 아닌지로 판단할 수도 있지만, 명시적인 boolean 값이 있으면 읽기 쉽고 실수도 줄어듭니다.

현재는 QUEUED 상태의 Task 중 가장 오래된 것 하나를 조회합니다.

```
agentId 기준 조회
status = QUEUED
createdAt 오름차순
1개 반환
```

아직 동시 Polling을 엄격히 막는 Lock 구조는 넣지 않았습니다. 이후 여러 Agent 프로세스가 같은 agentId로 동시에 Polling할 수 있는 상황을 고려하면 FOR UPDATE SKIP LOCKED 같은 방식이 필요합니다.


# 9. Agent가 임의 명령을 실행하지 않도록 한 이유

Agent 설계에서 가장 중요한 원칙 중 하나는 "원격 Shell이 되면 안 된다"는 것입니다.

나쁜 설계는 이런 형태입니다.

```json
{
  "command": "rm -rf /var/lib/mysql"
}
```

이런 방식은 매우 위험합니다. Control Plane이 실수하거나 공격자가 요청을 조작하면 Agent가 DB Host에서 임의 명령을 실행할 수 있습니다.

그래서 DB FleetOps Agent는 임의 command를 받지 않습니다. 대신 미리 정의된 Task Type만 받습니다.

```
COLLECT_LINUX_STATUS
MYSQL_LOGICAL_BACKUP
```

Go Agent 내부에서는 Task Type별 Handler를 등록합니다.

```
Task Polling
  ↓
Dispatcher
  ↓
Handler 선택
  ↓
Handler 실행
```

현재 구현된 Handler는 다음과 같습니다.

```
LinuxStatusHandler
MySQLBackupHandler
```

이 구조를 사용하면 새로운 기능을 추가할 때도 허용된 Handler를 명시적으로 추가해야 합니다. 즉, 기능 확장은 가능하지만 실행 범위는 통제됩니다.

# 10. Go Agent에도 Port 구조를 적용한 이유

Go Agent도 Java Control Plane과 같은 방향으로 설계했습니다.

```
application
  ↓
port
  ↓
infra
```

Go에서 port는 interface입니다.

```go
type HeartbeatPort interface {
    SendHeartbeat(ctx context.Context, agentInfo domain.AgentInfo) error
}
```

AgentService는 Heartbeat를 HTTP로 보내는지, 파일에 쓰는지, 테스트 fake로 처리하는지 알 필요가 없습니다. 단지 HeartbeatPort를 호출합니다.

이 구조를 둔 이유는 다음과 같습니다.

첫째, 테스트가 쉬워집니다. Go는 Mockito 같은 도구 없이 작은 fake struct를 직접 만들어 테스트하는 방식이 자연스럽습니다. Port interface가 있으면 fake 구현을 쉽게 넣을 수 있습니다.

둘째, 실제 구현을 교체하기 쉽습니다. 현재는 HTTP로 Control Plane에 요청하지만, 나중에 메시지 큐나 gRPC로 바꾸더라도 application 흐름은 유지할 수 있습니다.

셋째, Go Agent 코드가 커졌을 때 책임이 섞이는 것을 막을 수 있습니다. Heartbeat, Task Polling, Linux 수집, 백업 실행이 모두 main.go에 섞이면 유지보수가 어려워집니다.

# 11. Go의 internal 디렉토리를 사용한 이유

Go Agent 코드는 internal 디렉토리 아래에 두었습니다.

agent-go/internal
├── application
├── config
├── domain
├── infra
├── port
└── task

Go에서 internal은 특별한 의미가 있습니다. internal 아래의 패키지는 외부 모듈에서 import할 수 없습니다. Agent는 외부에서 가져다 쓰는 라이브러리가 아니라 독립 실행 프로그램입니다. 따라서 내부 구현을 외부에 노출할 필요가 없습니다.

Java의 package-private보다 강한 캡슐화라고 이해하면 됩니다.

# 12. main.go를 작게 유지한 이유

Go Agent의 main.go는 실행 조립만 담당합니다.

```
Config Load
  ↓
HTTP Client 생성
  ↓
Linux Collector 생성
  ↓
Task Dispatcher 생성
  ↓
AgentService 생성
  ↓
Register
  ↓
Heartbeat
  ↓
Task Polling
```

main.go 안에 HTTP 요청 코드나 Linux 상태 수집 코드, 백업 실행 코드를 직접 넣지 않았습니다.

그 이유는 실행 진입점과 실제 기능 구현을 분리하기 위해서입니다. main.go가 커지면 테스트하기 어렵고, 기능이 추가될수록 흐름을 파악하기 어려워집니다.

# 13. ControlPlaneClient가 여러 Port를 구현하는 방식

Go에는 Java의 implements 키워드가 없습니다. 어떤 struct가 interface가 요구하는 메서드를 가지고 있으면 자동으로 그 interface를 구현한 것으로 인정됩니다.

현재 ControlPlaneClient는 여러 역할을 동시에 수행합니다.

```
RegistrationPort
HeartbeatPort
TaskPort
```

즉, 다음 메서드들을 가지고 있습니다.

```
RegisterAgent()
SendHeartbeat()
FetchNextTask()
StartTask()
CompleteTask()
FailTask()
```

그래서 AgentService를 생성할 때 같은 객체를 여러 Port 자리에 넣을 수 있습니다.

```go
service := application.NewAgentService(
    controlPlaneClient,
    controlPlaneClient,
    controlPlaneClient,
    linuxInfoCollector,
    dispatcher,
)
```

처음 보면 같은 인자를 세 번 넣는 것이 어색해 보일 수 있습니다. 하지만 의미는 다릅니다.

```
첫 번째 controlPlaneClient
→ RegistrationPort 역할
두 번째 controlPlaneClient
→ HeartbeatPort 역할
세 번째 controlPlaneClient
→ TaskPort 역할
```

현재는 HTTP 호출 구현체가 하나라서 같은 객체가 여러 역할을 맡고 있습니다. 나중에 복잡해지면 RegistrationClient, HeartbeatClient, TaskClient로 나눌 수 있습니다.

# 14. Linux 상태 수집을 Stub에 가깝게 둔 이유

현재 LinuxStatusCollector는 완전한 Host 메트릭 수집기가 아닙니다.

```
CPU 사용률 = 0.0
Disk 사용률 = 0.0
Memory 사용률 = Go runtime 기준 계산
```

이렇게 단순하게 둔 이유는 이번 목표가 정확한 시스템 메트릭 계산이 아니라 Agent Task 처리 흐름을 완성하는 것이기 때문입니다.

정확한 Linux 상태 수집은 다음 요소가 필요합니다.

```
/proc/stat 기반 CPU 계산
/proc/meminfo 기반 Memory 계산
statfs 기반 Disk 계산
Linux와 macOS 개발환경 차이 처리
권한 문제 처리
```

이 부분까지 한 번에 구현하면 Agent 통신 흐름보다 OS별 예외 처리에 시간이 많이 들어갑니다. 그래서 먼저 Handler 구조와 결과 보고 흐름을 만들고, 실제 Host 메트릭은 후속 개선으로 남겨두었습니다.


# 15. MySQL 백업을 Stub Handler로 둔 이유

현재 MYSQL_LOGICAL_BACKUP Handler는 실제 mysqldump를 실행하지 않습니다. 대신 백업 파일명과 성공 메시지를 담은 JSON을 반환합니다.

```json
{
  "status": "CREATED",
  "backupFile": "/tmp/db-fleetops-backups/orders-20260704-173000.sql",
  "createdAt": "2026-07-04T17:30:00+09:00",
  "message": "stub mysql logical backup completed"
}
```

실제 백업 실행을 바로 넣지 않은 이유는 백업이 단순 명령 실행이 아니기 때문입니다. 실제 백업에는 다음 문제가 함께 따라옵니다.

```
DB 접속 Credential 전달 방식
비밀번호 로그 마스킹
백업 파일 저장 경로
디스크 용량 부족
mysqldump 실패 코드 처리
압축 여부
Checksum 생성
복원 검증
파일 권한
중복 실행 방지
```

이런 문제를 무시하고 exec.Command("mysqldump", ...)만 붙이면 단순히 동작해 보여도 운영 플랫폼 설계로는 부족합니다.

그래서 이번 단계에서는 Task Type과 Handler 구조를 먼저 고정하고, 실제 백업은 다음 단계에서 안전하게 확장하는 것이 맞다고 판단했습니다.

# 16. 현재 Agent 실행 흐름

현재 Go Agent는 아직 데몬처럼 계속 실행되지 않습니다.

현재 흐름은 다음과 같습니다.

1. 설정 로딩
2. AgentInfo 수집
3. Agent 등록
4. Heartbeat 전송
5. Task 1회 Polling
6. Task가 있으면 실행
7. 결과 보고
8. 종료

즉, 현재는 단발 실행 구조입니다.

이렇게 한 이유는 초기 디버깅을 쉽게 하기 위해서입니다. 계속 도는 루프를 먼저 만들면 API 호출, Task 처리, 실패 처리 중 어디에서 문제가 생겼는지 확인하기 어렵습니다.

단발 실행이 안정화되면 다음 단계에서 ticker 기반 반복 루프로 확장하면 됩니다.

```
Heartbeat ticker
Task polling ticker
Graceful shutdown
```

# 17. 테스트를 나눈 방식

Go Agent 테스트는 Java처럼 Spring Context를 띄우지 않습니다. 대신 작은 fake 구현체를 직접 만들어 application 흐름을 검증했습니다.

예를 들어 AgentService 테스트에서는 실제 HTTP 호출을 하지 않습니다. fakeRegistrationPort, fakeHeartbeatPort, fakeTaskPort를 만들어 호출 여부만 검증합니다.

이 방식의 장점은 빠르고 단순하다는 점입니다. Go에서는 interface가 작고 명시적이기 때문에 fake 구현을 직접 만드는 것이 자연스럽습니다.

현재 테스트는 다음 사항을 확인합니다.

- Register 시 LinuxInfoPort를 통해 AgentInfo를 수집하고 RegistrationPort 호출
- Heartbeat 시 LinuxInfoPort를 통해 AgentInfo를 수집하고 HeartbeatPort 호출
- Task Polling 시 Task가 없으면 아무 작업도 하지 않음
- Task가 있으면 Start → Dispatch → Complete 순서로 처리
- LinuxStatusHandler가 COLLECT_LINUX_STATUS만 처리
- MySQLBackupHandler가 MYSQL_LOGICAL_BACKUP만 처리
- 잘못된 JSON이면 Backup Handler가 에러 반환

이 테스트는 Agent가 임의 작업을 실행하지 않고, 정해진 Handler만 실행한다는 설계 의도를 검증합니다.

# 18. 현재 구현의 한계

현재 구현은 Agent 통신과 Task 실행의 기본 흐름입니다.

아직 남아 있는 한계는 다음과 같습니다.

- Agent가 실행할 때마다 새로 등록됨
- agentId와 agentToken을 로컬에 저장하지 않음
- Heartbeat와 Polling이 반복 루프가 아님
- Task Polling이 진짜 Long Polling이 아님
- AgentTask와 OperationJob이 아직 자동 연결되지 않음
- Linux 상태 수집이 정확한 Host 기준이 아님
- MySQL 백업은 Stub임
- Agent Token 보안이 약함

특히 Agent가 매번 새로 등록되는 문제는 곧 개선해야 합니다. 실제 Agent는 최초 등록 후 발급받은 agentId, agentToken을 로컬 설정 파일이나 안전한 저장소에 보관하고, 다음 실행부터는 재등록하지 않고 Heartbeat를 보내야 합니다.