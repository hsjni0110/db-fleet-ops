# Safe Configuration Apply 설계

# 1. 왜 Configuration Apply가 필요한가

DB FleetOps는 단순히 DB 상태를 조회하는 모니터링 도구가 아니라, 여러 DB 인스턴스의 운영 작업을 플랫폼에서 안전하게 처리하는 것을 목표로 합니다.

이번에는 Configuration Drift 기능을 통해 DB 설정이 표준 기준과 일치하는지 확인했습니다.

흐름은 다음과 같습니다.

```text
Configuration Profile
  ↓
실제 DB 설정값 수집
  ↓
Snapshot 저장
  ↓
Profile과 Snapshot 비교
  ↓
Drift 탐지
```

이 기능을 통해 다음 질문에는 답할 수 있게 되었습니다.

```text
현재 DB 설정이 운영 기준과 다른가?
어떤 Parameter가 다른가?
expectedValue와 actualValue는 무엇인가?
```

하지만 Drift를 탐지하는 것만으로는 운영 문제가 완전히 해결되지 않습니다.

운영자는 결국 다음 질문을 하게 됩니다.

```text
이 설정을 기준값으로 되돌릴 수 있는가?
바꿔도 안전한 설정인가?
누가, 왜, 어떤 값으로 바꾸었는가?
변경 전 값은 무엇이었는가?
변경 후 실제로 반영되었는가?
```

이번 작업은 이 질문에 답하기 위해 만들었습니다.

단순히 `SET GLOBAL`을 실행하는 기능이 아닙니다.

핵심은 다음입니다.

```text
DB 설정 변경을 통제 가능한 운영 Job으로 모델링하는 것
```

---

# 2. 기존 수동 설정 변경 방식의 문제

일반적인 DB 운영에서는 설정 변경을 다음처럼 처리할 수 있습니다.

```text
운영자 SSH 접속
  ↓
mysql client 접속
  ↓
SHOW VARIABLES 확인
  ↓
SET GLOBAL 실행
  ↓
변경 여부 수동 확인
  ↓
작업 내역 수기 기록
```

이 방식은 빠르지만 위험합니다.

문제는 다음과 같습니다.

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

예를 들어 `slow_query_log`를 `OFF`에서 `ON`으로 변경했다고 하겠습니다.

단순히 명령만 실행하면 다음 정보가 남지 않을 수 있습니다.

```text
변경 전 값이 무엇이었는가?
누가 요청했는가?
왜 요청했는가?
명령 실행 후 실제 DB 값이 어떻게 되었는가?
문제가 생기면 어떤 값으로 되돌려야 하는가?
```

DB 설정 변경은 단순 CRUD가 아닙니다.  
운영 DB의 동작을 바꾸는 작업입니다.

그래서 Phase 7에서는 설정 변경을 API에서 즉시 실행하지 않고, OperationJob으로 생성한 뒤 Worker가 안전한 순서로 처리하도록 설계했습니다.

---

# 3. 목표

MySQL 설정값을 플랫폼에서 안전하게 변경할 수 있는 구조를 만드는 것입니다.

이번에 구현한 범위는 다음과 같습니다.

```text
CONFIGURATION_APPLY JobType 추가
Configuration Apply 요청 API 추가
Apply 요청 DTO 추가
ProfileParameter 기반 검증
dynamic=true 검증
applyAllowed=true 검증
ParameterValueType별 targetValue 검증
동일 DB Apply 중복 실행 차단
MySQL SET GLOBAL Adapter 추가
변경 전 Snapshot 저장
변경 후 Snapshot 저장
변경 후 실제값 검증
ConfigurationApply 결과 저장
ConfigurationApplyItem 결과 저장
Apply 결과 조회 API 추가
Flow 테스트 추가
API 문서 작성
```

반대로 이번 작업에서 제외한 범위도 명확히 했습니다.

```text
Static Parameter 적용
my.cnf 파일 수정
DB 재시작
자동 Rollback
승인 Workflow
SaltStack 적용
Agent 기반 설정 파일 변경
PostgreSQL 설정 변경
Secret Rotation
```

이렇게 범위를 나눈 이유는 설정 변경의 위험도를 단계적으로 통제하기 위해서입니다.

Dynamic Parameter는 `SET GLOBAL`로 즉시 변경할 수 있지만, Static Parameter는 설정 파일 수정과 DB 재시작이 필요할 수 있습니다. DB 재시작은 서비스 영향도가 크고, 실패 시 복구 절차도 필요합니다.

따라서 이 구조에는 Dynamic Parameter만 지원하고, Static Parameter는 명확히 거부하는 구조로 설계했습니다.

---

# 4. 읽기 중심 작업과 쓰기 중심 작업

이전에 했던 작업은 읽기 중심 작업입니다.

```text
Profile 기준값
  ↓
실제 DB 설정 조회
  ↓
Drift 탐지
```

이번에 했던 작업은 쓰기 중심 작업입니다.

```text
Apply 요청
  ↓
변경 가능 여부 검증
  ↓
설정 변경 Job 생성
  ↓
변경 전 Snapshot 저장
  ↓
SET GLOBAL 실행
  ↓
변경 후 Snapshot 저장
  ↓
검증
  ↓
Apply 결과 저장
```

이는 연결되지만 책임은 다릅니다.

| 구분 | 이전 작업 | 이후 작업 |
|---|---|---|
| 목적 | 설정 위반 탐지 | 설정 변경 적용 |
| 작업 성격 | Read-only | Write operation |
| 위험도 | 낮음 | 높음 |
| 실행 방식 | Job 권장 | 반드시 Job |
| 주요 결과 | Drift | Apply Result |
| 핵심 도메인 | Profile, Snapshot, Drift | Apply, ApplyItem |
| 실패 처리 | 조회 실패, 비교 실패 | 변경 실패, 검증 실패 |


이전에 만든 작업에서의 도메인은 아래와 같습니다.

```text
ConfigurationProfile
ConfigurationProfileParameter
ConfigurationSnapshot
ConfigurationSnapshotItem
ParameterValueType
ConfigurationValueComparator
```

그리고 이번에 다음 도메인을 추가했습니다.

```text
ConfigurationApply
ConfigurationApplyItem
ConfigurationApplyStatus
ConfigurationApplyItemStatus
```

---

# 5. 설정 변경을 즉시 실행하지 않은 이유

가장 피해야 할 구조는 다음입니다.

```http
POST /api/v1/database-instances/1/configuration
```

```json
{
  "parameterName": "slow_query_log",
  "targetValue": "ON"
}
```

그리고 API 요청 안에서 바로 다음 명령을 실행하는 방식입니다.

```sql
SET GLOBAL slow_query_log = 'ON';
```

이 방식은 간단하지만 운영 플랫폼으로는 위험합니다.

문제는 다음과 같습니다.

```text
HTTP timeout 발생 가능
실패 시 상태 추적 어려움
변경 전 값 저장 누락 가능
변경 후 검증 누락 가능
Audit 누락 가능
동시 변경 제어 어려움
재시도 정책 부재
```

그래서 다음 구조를 선택했습니다.

```http
POST /api/v1/database-instances/{databaseId}/operations/configuration-applies
```

응답은 변경 완료가 아니라 Job 생성 결과입니다.

```json
{
  "jobId": 31,
  "jobType": "CONFIGURATION_APPLY",
  "status": "QUEUED"
}
```

즉, API 요청은 설정 변경을 직접 수행하지 않습니다.

```text
API 요청
  ↓
OperationJob 생성
  ↓
QUEUED 상태 저장
  ↓
Worker가 Job 실행
```

이 구조를 사용하면 요청 접수와 실제 실행을 분리할 수 있습니다.

---

# 6. OperationJob을 재사용한 이유

DB FleetOps에는 이미 백업과 설정 점검을 위한 OperationJob 구조가 있었습니다.

기존 JobType은 다음과 같았습니다.

```text
BACKUP
CONFIGURATION_CHECK
```

여기에 다음 JobType을 추가했습니다.

```text
CONFIGURATION_APPLY
```

설정 변경도 운영 작업이기 때문에 기존 Job Engine을 재사용하는 것이 자연스럽다고 판단했습니다.

OperationJob은 다음 책임을 가집니다.

```text
작업 종류 구분
대상 databaseId 저장
요청자 저장
Idempotency-Key 처리
작업 상태 관리
Worker Claim 처리
실패 및 재시도 처리
결과 메시지 저장
```

설정 변경 요청도 이 흐름을 그대로 사용할 수 있습니다.

```text
Apply 요청
  ↓
OperationJob 생성
  ↓
QUEUED
  ↓
Worker Claim
  ↓
RUNNING
  ↓
Apply 실행
  ↓
SUCCEEDED 또는 FAILED
```

새로운 별도 Job 시스템을 만들지 않고 기존 OperationJob을 확장한 이유는 다음과 같습니다.

```text
운영 작업 실행 방식 통일
Job 상태 조회 방식 통일
Idempotency 처리 재사용
Worker Claim 구조 재사용
Audit 흐름 재사용
향후 Dashboard에서 Job 목록 통합 가능
```

---

# 7. Job 상태와 Apply 상태를 분리한 이유

중요한 설계 중 하나는 OperationJob 상태와 ConfigurationApply 상태를 분리한 것입니다.

OperationJob은 작업 실행 관점의 상태입니다.

```text
QUEUED
RUNNING
SUCCEEDED
FAILED
CANCELLED
TIMED_OUT
```

ConfigurationApply는 설정 변경 도메인 관점의 상태입니다.

```text
REQUESTED
RUNNING
SUCCEEDED
PARTIALLY_SUCCEEDED
FAILED
CANCELLED
```

둘을 분리한 이유는 일부 Parameter만 실패할 수 있기 때문입니다.

예를 들어 3개 Parameter를 변경한다고 하겠습니다.

```text
slow_query_log   → 성공
long_query_time  → 성공
max_connections  → 검증 실패
```

이 경우 전체 Job을 성공으로 볼 수는 없습니다. 운영 관점에서는 실패를 놓치면 안 되기 때문입니다.

그래서 OperationJob은 보수적으로 `FAILED`로 처리할 수 있습니다.

하지만 Apply 도메인에서는 실제 결과를 더 정확하게 표현해야 합니다.

```text
OperationJob.status = FAILED
ConfigurationApply.status = PARTIALLY_SUCCEEDED
```

이렇게 분리하면 Job Engine은 단순하고 보수적으로 유지하면서, 설정 변경의 세부 결과는 Apply 도메인에서 표현할 수 있습니다.

---

# 8. ConfigurationApply 도메인 설계

`ConfigurationApply`는 하나의 설정 변경 작업 결과를 표현합니다.

주요 필드는 다음과 같습니다.

```text
id
databaseId
operationJobId
requestedBy
reason
status
totalCount
successCount
failedCount
skippedCount
beforeSnapshotId
afterSnapshotId
createdAt
startedAt
completedAt
```

이 도메인은 다음 질문에 답하기 위해 존재합니다.

```text
어떤 DB에 대한 설정 변경인가?
어떤 OperationJob에서 실행되었는가?
누가 요청했는가?
왜 요청했는가?
총 몇 개 Parameter를 변경하려 했는가?
몇 개가 성공했는가?
몇 개가 실패했는가?
변경 전 Snapshot은 무엇인가?
변경 후 Snapshot은 무엇인가?
언제 시작되고 언제 끝났는가?
```

중요한 점은 `beforeSnapshotId`와 `afterSnapshotId`를 Apply에 직접 저장했다는 것입니다.

이렇게 하면 설정 변경 작업과 변경 전후 상태를 명확히 연결할 수 있습니다.

```text
ConfigurationApply
  ├── beforeSnapshotId
  └── afterSnapshotId
```

---

# 9. ConfigurationApplyItem 도메인 설계

`ConfigurationApplyItem`은 Parameter 단위 변경 결과를 표현합니다.

주요 필드는 다음과 같습니다.

```text
id
applyId
parameterName
requestedValue
beforeValue
afterValue
valueType
dynamic
applyAllowed
applyStatus
failureCode
failureMessage
createdAt
appliedAt
verifiedAt
```

Apply는 여러 Item을 가질 수 있습니다.

```text
ConfigurationApply
  ├── ConfigurationApplyItem(slow_query_log)
  ├── ConfigurationApplyItem(long_query_time)
  └── ConfigurationApplyItem(max_connections)
```

이 구조를 둔 이유는 Parameter마다 결과가 다를 수 있기 때문입니다.

예를 들어 다음처럼 저장할 수 있습니다.

```text
slow_query_log
  requestedValue = ON
  beforeValue = OFF
  afterValue = ON
  applyStatus = VERIFIED

long_query_time
  requestedValue = 1.0
  beforeValue = 10.000000
  afterValue = 1.000000
  applyStatus = VERIFIED

max_connections
  requestedValue = 500
  beforeValue = 151
  afterValue = 151
  applyStatus = FAILED
  failureCode = VERIFY_VALUE_MISMATCH
```

이렇게 하면 운영자는 어떤 항목이 성공했고, 어떤 항목이 실패했는지 정확히 볼 수 있습니다.

---

# 10. ApplyItem 상태를 세분화한 이유

`ConfigurationApplyItemStatus`는 다음 상태를 가집니다.

```text
PENDING
APPLIED
VERIFIED
SKIPPED
FAILED
UNSUPPORTED
```

각 상태의 의미는 다음과 같습니다.

| 상태 | 의미 |
|---|---|
| PENDING | 아직 적용 전 |
| APPLIED | `SET GLOBAL` 실행은 성공했지만 검증 전 |
| VERIFIED | 변경 후 실제값이 요청값과 일치함 |
| SKIPPED | 정책상 건너뜀 |
| FAILED | 적용 또는 검증 실패 |
| UNSUPPORTED | Static Parameter 등 현재 MVP에서 지원하지 않음 |

여기서 가장 중요한 구분은 `APPLIED`와 `VERIFIED`입니다.

`APPLIED`는 명령 실행이 성공했다는 뜻입니다.

```text
SET GLOBAL slow_query_log = 'ON'
  ↓
명령 실행 성공
  ↓
APPLIED
```

하지만 이것만으로는 충분하지 않습니다.

반드시 변경 후 실제 값을 다시 조회해야 합니다.

```text
SHOW GLOBAL VARIABLES
  ↓
slow_query_log = ON 확인
  ↓
VERIFIED
```

즉, 성공 기준은 `APPLIED`가 아니라 `VERIFIED`입니다.

---

# 11. ProfileParameter 기반 검증을 둔 이유

설정 변경 요청에서 사용자가 임의의 Parameter를 보낼 수 있습니다.

예를 들어 다음 요청은 위험합니다.

```json
{
  "parameterName": "sql_mode",
  "targetValue": "abc'; DROP DATABASE mysql; --"
}
```

따라서 Apply 요청은 반드시 Profile에 등록된 Parameter만 허용하도록 했습니다.

검증 조건은 다음과 같습니다.

```text
ProfileParameter에 존재
AND dynamic = true
AND applyAllowed = true
AND valueType 검증 통과
```

이렇게 한 이유는 설정 변경 가능 여부를 사용자 입력이 아니라 운영 정책 기준으로 판단하기 위해서입니다.

ProfileParameter는 단순 기준값만 저장하지 않습니다.

```text
parameterName
expectedValue
valueType
required
dynamic
applyAllowed
description
```

여기서 `dynamic`과 `applyAllowed`가 중요합니다.

```text
dynamic
  - DB 재시작 없이 변경 가능한가?

applyAllowed
  - 플랫폼에서 변경을 허용할 것인가?
```

둘은 다릅니다.

어떤 설정은 MySQL에서 Dynamic일 수 있지만, 운영 정책상 플랫폼에서 자동 변경을 막고 싶을 수 있습니다. 반대로 Dynamic이 아니면 애초에 이번 작업에서 적용하지 않습니다.

---

# 12. profileId를 Apply 요청에 포함한 이유

Apply 요청에는 `profileId`를 포함하도록 했습니다.

```json
{
  "profileId": 1,
  "requestedBy": "local-user",
  "reason": "enable slow query log",
  "parameters": [
    {
      "parameterName": "slow_query_log",
      "targetValue": "ON"
    }
  ]
}
```

처음에는 parameterName만 받아서 검증할 수도 있습니다.

하지만 이 방식은 애매합니다.

```text
slow_query_log가 여러 Profile에 등록되어 있다면
어떤 Profile 기준으로 검증해야 하는가?
```

운영 환경, 개발 환경, DBMS 버전별로 Profile이 다를 수 있습니다.

```text
mysql-production-standard
mysql-staging-standard
mysql-local-standard
```

따라서 Apply 요청은 어떤 Profile 기준으로 변경하는지 명확해야 합니다.

그래서 `profileId`를 요청에 포함했습니다.

검증은 다음 기준으로 수행합니다.

```text
profileId + parameterName
```

즉, 요청한 Parameter가 해당 Profile에 등록되어 있어야만 Apply Job을 생성할 수 있습니다.

---

# 13. ParameterValueType별 검증을 둔 이유

DB 설정값은 문자열로 들어오지만 실제 의미는 다를 수 있습니다.

이전에 만든 `ParameterValueType`을 재사용했습니다.

```text
STRING
NUMBER
BOOLEAN
```

각 타입별 검증 방식은 다릅니다.

## BOOLEAN

다음 값을 허용합니다.

```text
ON
OFF
TRUE
FALSE
1
0
YES
NO
Y
N
```

허용되지 않는 값은 거부합니다.

```text
enabled
disabled
maybe
```

이 값들은 사람에게는 의미가 있어 보여도 DB 설정값으로는 명확하지 않습니다.

## NUMBER

숫자는 `BigDecimal`로 파싱 가능한 값만 허용합니다.

허용 예시는 다음과 같습니다.

```text
1
1.0
300
0.5
```

거부 예시는 다음과 같습니다.

```text
abc
10sec
one
```

숫자 타입을 문자열 그대로 통과시키면 `SET GLOBAL` 실행 시점에 실패하거나, 의도하지 않은 값이 들어갈 수 있습니다.

## STRING

문자열은 가장 위험합니다.

따라서 MVP에서는 최소한 다음 패턴을 차단했습니다.

```text
;
--
/*
*/
```

문자열 타입은 장기적으로 더 강한 whitelist 정책이 필요합니다.

---

# 14. 동일 DB Apply 중복 실행을 차단한 이유

설정 변경은 같은 DB에 동시에 여러 개 실행되면 위험합니다.

예를 들어 두 Job이 동시에 실행된다고 하겠습니다.

```text
Job A: slow_query_log = ON
Job B: slow_query_log = OFF
```

두 Job이 동시에 실행되면 최종 상태를 예측하기 어렵습니다.

또 다른 예시도 있습니다.

```text
Job A: long_query_time = 1.0
Job B: long_query_time = 10.0
```

어떤 Job이 나중에 실행되었는지에 따라 결과가 달라집니다.

그래서 같은 `databaseId`에 대해 다음 상태의 Apply가 있으면 새 Apply 요청을 차단했습니다.

```text
REQUESTED
RUNNING
```

구현은 `ConfigurationApplyRepository`에 다음 메서드를 두었습니다.

```text
existsByDatabaseIdAndStatusIn
```

이 정책의 목적은 설정 변경 작업을 직렬화하는 것입니다.

```text
동일 DB에 대해 동시에 하나의 Apply만 허용
```

조회성 작업인 Configuration Check나 Health Check는 병행 가능하지만, 변경성 작업은 보수적으로 차단하는 것이 안전하다고 판단했습니다.

---

# 15. MySQL SET GLOBAL Adapter를 분리한 이유

설정 변경 명령은 DBMS마다 다릅니다.

MySQL은 다음을 사용합니다.

```sql
SET GLOBAL slow_query_log = 'ON';
```

PostgreSQL은 다른 접근이 필요합니다.

```sql
ALTER SYSTEM SET ...
SELECT pg_reload_conf();
```

따라서 설정 변경 로직을 policy나 operation service 안에 직접 넣지 않았습니다.

대신 Port/Adapter 구조로 분리했습니다.

```text
DatabaseConfigurationApplyPort
  ↓
MySqlConfigurationApplyAdapter
```

Port는 다음 책임을 가집니다.

```text
DBMS별 설정 변경 명령 실행
```

MySQL Adapter는 다음 책임을 가집니다.

```text
ManagedDatabase 조회
Credential 조회
JDBC 연결 생성
Parameter whitelist 확인
Value 정규화
SET GLOBAL 실행
```

이렇게 하면 나중에 PostgreSQL을 추가할 때 기존 Apply 실행 흐름을 크게 바꾸지 않아도 됩니다.

```text
DatabaseConfigurationApplyPort
  ├── MySqlConfigurationApplyAdapter
  └── PostgreSqlConfigurationApplyAdapter
```

---

# 16. Parameter whitelist를 둔 이유

`SET GLOBAL`은 Parameter 이름을 PreparedStatement placeholder로 안전하게 바인딩하기 어렵습니다.

예를 들어 다음처럼 처리하기 어렵습니다.

```java
jdbcTemplate.update(
    "SET GLOBAL ? = ?",
    parameterName,
    targetValue
);
```

설정 변수명은 값이 아니라 SQL 식별자에 가깝기 때문입니다.

따라서 사용자 입력을 그대로 SQL에 붙이면 위험합니다.

그래서 MySQL Adapter에 whitelist를 두었습니다.

```text
slow_query_log
long_query_time
max_connections
```

즉, 코드에서 허용한 Parameter만 `SET GLOBAL`로 실행할 수 있습니다.

여기에 더해 다음 방어도 적용했습니다.

```text
parameterName 정규화
valueType별 값 검증
STRING 위험 패턴 차단
문자열 literal escaping
```

현재 whitelist는 코드에 고정되어 있습니다.  
후속 개선에서는 ProfileParameter나 별도 ApplyPolicy 테이블로 분리할 수 있습니다.

---

# 17. 변경 전 Snapshot을 저장한 이유

설정 변경 전에 반드시 Snapshot을 저장합니다.

```text
beforeSnapshotId
```

예를 들어 변경 전 값이 다음과 같다고 하겠습니다.

```text
slow_query_log = OFF
long_query_time = 10.000000
```

Apply 요청은 다음과 같습니다.

```text
slow_query_log = ON
long_query_time = 1.0
```

변경 전 Snapshot을 저장하면 다음 질문에 답할 수 있습니다.

```text
변경 전 값이 무엇이었는가?
문제가 생기면 어떤 값으로 되돌려야 하는가?
변경 전후 차이를 어떻게 설명할 것인가?
Audit 근거는 무엇인가?
```

이 값은 `ConfigurationApplyItem.beforeValue`에도 저장합니다.

```text
beforeValue = OFF
```

이 구조는 향후 Rollback Plan을 만들 때도 사용할 수 있습니다.

---

# 18. 변경 후 Snapshot을 저장한 이유

`SET GLOBAL`을 실행한 뒤에는 다시 Snapshot을 저장합니다.

```text
afterSnapshotId
```

이유는 명령 실행 성공과 실제 반영 성공이 다를 수 있기 때문입니다.

예를 들어 명령은 성공했지만 DBMS가 값을 정규화할 수 있습니다.

```text
requestedValue = 1.0
afterValue = 1.000000
```

문자열로는 다르지만 숫자 의미로는 같습니다.

또는 명령 실행 후에도 값이 그대로일 수 있습니다.

```text
requestedValue = 500
afterValue = 151
```

이 경우는 검증 실패입니다.

따라서 변경 후 Snapshot을 수집하고, `requestedValue`와 `afterValue`를 비교합니다.

성공 기준은 다음입니다.

```text
SET GLOBAL 실행 성공
AND
afterValue가 requestedValue와 의미상 일치
```

---

# 19. ConfigurationValueComparator를 재사용한 이유

Drift 비교를 위해 `ConfigurationValueComparator`를 만들었습니다.

이 컴포넌트는 타입별 비교를 담당합니다.

```text
STRING
NUMBER
BOOLEAN
```

변경 후 검증에도 같은 비교 규칙이 필요합니다.

예를 들어 다음은 같은 값으로 봐야 합니다.

```text
requestedValue = ON
afterValue = 1
valueType = BOOLEAN
```

또 다음도 같은 값으로 봐야 합니다.

```text
requestedValue = 1.0
afterValue = 1.000000
valueType = NUMBER
```

따라서 새로운 비교 로직을 만들지 않고 기존 Comparator를 재사용했습니다.

이렇게 하면 Drift 비교와 Apply 검증의 판단 기준이 일관됩니다.

```text
Drift 판단 기준
Apply 검증 기준
  → 동일한 Comparator 사용
```

---

# 20. ConfigurationApplyJobExecutor를 분리한 이유

Worker가 Job을 Claim하면 JobType에 따라 다른 실행을 해야 합니다.

Configuration Check는 Drift를 생성합니다.

```text
CONFIGURATION_CHECK
  ↓
Snapshot 수집
  ↓
Profile 비교
  ↓
Drift 저장
```

Configuration Apply는 설정 변경을 수행합니다.

```text
CONFIGURATION_APPLY
  ↓
Apply 생성
  ↓
Before Snapshot
  ↓
SET GLOBAL
  ↓
After Snapshot
  ↓
검증
  ↓
Apply 결과 저장
```

이 로직을 모두 `OperationWorkerService` 안에 넣으면 Worker가 너무 많은 책임을 갖게 됩니다.

그래서 `ConfigurationApplyJobExecutor`를 분리했습니다.

`OperationWorkerService`의 책임은 다음입니다.

```text
Job Claim
JobType 분기
Job 성공/실패 상태 처리
Audit 기록
```

`ConfigurationApplyJobExecutor`의 책임은 다음입니다.

```text
payload 파싱
Apply 요청 재검증
ConfigurationApply 생성
ConfigurationApplyItem 생성
Before Snapshot 수집
DBMS별 ApplyPort 실행
After Snapshot 수집
변경 결과 검증
Apply 결과 저장
```

이렇게 나누면 Worker는 Job Engine 역할에 집중하고, Apply 실행 세부 흐름은 별도 컴포넌트가 담당합니다.

---

# 21. Apply 실행 흐름

Phase 7의 실제 실행 흐름은 다음과 같습니다.

```text
OperationJob Claim
  ↓
ConfigurationApplyJobExecutor.execute(job)
  ↓
payloadJson 파싱
  ↓
ManagedDatabase 조회
  ↓
DatabaseEngine 확인
  ↓
ConfigurationApplyValidationService 재검증
  ↓
ConfigurationApply 생성
  ↓
ConfigurationApplyItem 생성
  ↓
Before Snapshot 수집
  ↓
beforeValue 기록
  ↓
DatabaseConfigurationApplyPort 선택
  ↓
Parameter별 SET GLOBAL 실행
  ↓
After Snapshot 수집
  ↓
afterValue 검증
  ↓
ConfigurationApply 완료 처리
  ↓
OperationJob 성공/실패 처리
```

여기서 “재검증”을 하는 점이 중요합니다.

Apply Job 생성 시점에 이미 검증을 했지만, 실행 시점에 다시 검증합니다.

이유는 Job 생성 후 실행 전까지 상태가 바뀔 수 있기 때문입니다.

```text
ProfileParameter가 비활성화될 수 있음
applyAllowed 정책이 바뀔 수 있음
동일 DB에 다른 Apply가 먼저 실행될 수 있음
```

따라서 실행 직전에도 ValidationService를 다시 호출합니다.

---

# 22. Idempotency-Key를 적용한 이유

Configuration Apply Job 생성에도 Idempotency-Key를 적용했습니다.

네트워크 문제나 사용자의 중복 클릭으로 같은 요청이 여러 번 들어올 수 있기 때문입니다.

예를 들어 다음 상황이 발생할 수 있습니다.

```text
사용자가 Apply 버튼 클릭
  ↓
응답 지연
  ↓
다시 클릭
  ↓
동일 Apply 요청 2번 전송
```

Idempotency-Key가 없다면 같은 설정 변경 Job이 중복 생성될 수 있습니다.

```text
CONFIGURATION_APPLY Job #1
CONFIGURATION_APPLY Job #2
```

이는 운영 DB 설정 변경에서는 위험합니다.

그래서 기존 OperationJob의 중복 방지 정책을 그대로 사용했습니다.

```text
databaseId + jobType + idempotencyKey
```

같은 키로 이미 생성된 Job이 있으면 새로 만들지 않고 기존 Job을 반환합니다.

---

# 23. 실패 처리 정책

설정 변경은 일부만 성공할 수 있습니다.

예를 들어 다음 상황입니다.

```text
slow_query_log   VERIFIED
long_query_time  VERIFIED
max_connections  FAILED
```

이때 보수적으로 Job을 실패 처리합니다.

```text
OperationJob.status = FAILED
```

하지만 Apply 도메인에는 세부 결과를 남깁니다.

```text
ConfigurationApply.status = PARTIALLY_SUCCEEDED
successCount = 2
failedCount = 1
```

이 정책을 선택한 이유는 설정 변경 작업에서는 실패를 놓치면 안 되기 때문입니다.

Job 목록에서 `SUCCEEDED`로 보이면 운영자는 모든 설정 변경이 성공했다고 오해할 수 있습니다.

따라서 Job은 보수적으로 실패 처리하고, 세부 성공/실패는 Apply 결과 조회 API에서 확인하도록 했습니다.

---

# 24. Apply 결과 조회 API를 분리한 이유

Phase 7에서는 두 가지 조회 API를 제공합니다.

```http
GET /api/v1/jobs/{jobId}/configuration-apply
GET /api/v1/configuration-applies/{applyId}
```

Job 기준 조회는 운영 Job 화면에서 사용하기 좋습니다.

```text
Job 목록
  ↓
Job 상세
  ↓
Configuration Apply 결과 조회
```

Apply ID 기준 조회는 Apply 도메인 자체를 직접 추적할 때 사용합니다.

```text
Alert
Audit
Runbook
  ↓
applyId 기준 상세 조회
```

응답에는 Apply 전체 결과와 Item별 결과가 포함됩니다.

```text
ConfigurationApply
  ↓
ConfigurationApplyItem 목록
```

이렇게 하면 운영자는 다음 정보를 한 번에 확인할 수 있습니다.

```text
변경 전 값
요청 값
변경 후 값
성공 여부
실패 사유
적용 시간
검증 시간
```

---

# 25. 테스트 전략

테스트를 세 가지 방향으로 나누었습니다.

## Validation 테스트

설정 변경 요청이 안전한지 검증합니다.

```text
Dynamic Parameter 요청 성공
Static Parameter 요청 거부
Profile에 없는 Parameter 거부
applyAllowed=false 거부
잘못된 BOOLEAN 값 거부
잘못된 NUMBER 값 거부
중복 Parameter 요청 거부
동일 DB Apply 중복 실행 차단
```

이 테스트가 중요한 이유는 설정 변경 기능은 실행보다 검증이 먼저이기 때문입니다.

## Adapter 테스트

MySQL Adapter는 실제 DB 연결 없이 우선 입력 방어 중심으로 테스트했습니다.

```text
허용되지 않은 parameter 차단
잘못된 BOOLEAN 값 차단
잘못된 NUMBER 값 차단
Unsafe STRING 값 차단
databaseId 누락 차단
valueType 누락 차단
```

실제 MySQL `SET GLOBAL` 통합 테스트는 Docker MySQL이나 Testcontainers가 필요하므로 후속으로 분리하는 것이 좋다고 판단했습니다.

## Flow 테스트

Apply 결과 저장 흐름을 검증했습니다.

```text
beforeValue 저장
afterValue 저장
beforeSnapshotId 저장
afterSnapshotId 저장
ApplyItem VERIFIED 저장
ApplyItem FAILED 저장
Apply 결과 조회
```

이 테스트는 실제 MySQL 연결이 아니라 도메인 저장 흐름을 고정하는 목적입니다.

---

# 26. MVP의 한계

현재 MVP에는 명확한 한계가 있습니다.

```text
Static Parameter 적용 불가
my.cnf 수정 불가
DB 재시작 불가
자동 Rollback 없음
승인 Workflow 없음
PostgreSQL Apply 미지원
SET GLOBAL whitelist가 코드에 고정됨
공통 Error Response 미정의
실제 MySQL SET GLOBAL 통합 테스트는 아직 분리됨
```

특히 Static Parameter는 의도적으로 제외했습니다.

Static Parameter를 지원하려면 다음 기능이 필요합니다.

```text
설정 파일 위치 탐지
설정 파일 수정
문법 검증
DB 재시작
재시작 실패 시 복구
Agent 기반 파일 작업
승인 Workflow
```

이 범위는 MVP를 넘어서는 작업입니다.

---

# 27. 후속 개선 방향

이후 자연스럽게 확장할 수 있는 방향은 다음과 같습니다.

```text
Drift 기반 Apply API 추가
Parameter whitelist를 DB 정책 테이블로 분리
Approval Workflow 추가
Rollback Plan 생성
변경 전후 Diff API 추가
Static Parameter 변경 지원
Agent 기반 my.cnf 수정
PostgreSQL ALTER SYSTEM 지원
공통 Error Response 적용
Apply 실패 유형별 retry 정책 분리
```

특히 Drift 기반 Apply API의 예상 흐름은 다음과 같습니다.

```text
ConfigurationDrift 조회
  ↓
NON_COMPLIANT Item 선택
  ↓
dynamic / applyAllowed 확인
  ↓
Configuration Apply Job 생성
  ↓
SET GLOBAL 실행
  ↓
재검증
```

이 기능까지 구현하면 “탐지 → 변경 → 검증” 흐름이 하나로 이어집니다.

---

# 28. 정리

이번에 DB 설정 변경을 단순 API로 열지 않았습니다.

대신 설정 변경을 다음 구조로 모델링했습니다.

```text
OperationJob
  - 설정 변경 실행 단위

ConfigurationApply
  - 설정 변경 결과 요약

ConfigurationApplyItem
  - Parameter별 변경 결과

ConfigurationSnapshot
  - 변경 전후 실제 DB 설정값

DatabaseConfigurationApplyPort
  - DBMS별 설정 적용 Adapter
```

흐름은 다음과 같습니다.

```text
Apply 요청
  ↓
Validation
  ↓
OperationJob 생성
  ↓
Worker Claim
  ↓
Before Snapshot
  ↓
SET GLOBAL
  ↓
After Snapshot
  ↓
Verification
  ↓
Apply Result 저장
  ↓
Job 성공/실패 처리
```

가장 중요한 설계 판단은 다음입니다.

```text
설정 변경을 API로 열어둔 것이 아니라,
설정 변경을 통제 가능한 운영 Job으로 모델링했다.
```

이 구조 덕분에 DB FleetOps는 단순히 DB 상태를 보는 도구에서 벗어나, 운영 변경 작업까지 안전하게 관리할 수 있는 플랫폼으로 확장되었습니다.