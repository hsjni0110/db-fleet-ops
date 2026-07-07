# Configuration Apply API

## 개요

Configuration Apply API는 등록된 DB 인스턴스의 설정값을 변경하기 위한 API입니다.

이 API의 핵심은 단순히 설정값을 바꾸는 것이 아니라, 설정 변경을 안전한 Operation Job으로 생성하고 실행 결과를 추적하는 것입니다.

현재 지원 DBMS는 다음과 같습니다.

- MySQL

현재 지원 방식은 다음과 같습니다.

- MySQL Dynamic Parameter에 대한 `SET GLOBAL` 적용
- 변경 전 Snapshot 저장
- 변경 후 Snapshot 저장
- 변경 후 실제값 재검증
- Apply 결과 조회

---

## 공통 전제

요청한 `databaseId`는 DB FleetOps Inventory에 등록되어 있어야 합니다.

대상 DB는 `ACTIVE` 상태여야 합니다.

변경 요청은 즉시 DB에 적용되지 않고, 먼저 `CONFIGURATION_APPLY` Operation Job으로 생성됩니다.

설정 변경 처리 흐름은 다음과 같습니다.

```text
Apply 요청
  ↓
ManagedDatabase 조회
  ↓
ACTIVE 상태 확인
  ↓
Idempotency-Key 중복 확인
  ↓
Configuration Profile 기준 검증
  ↓
OperationJob 생성
  ↓
status = QUEUED
  ↓
Worker가 Job Claim
  ↓
변경 전 Snapshot 저장
  ↓
SET GLOBAL 실행
  ↓
변경 후 Snapshot 저장
  ↓
변경 결과 검증
  ↓
ConfigurationApply 결과 저장
  ↓
OperationJob 성공/실패 처리
```

---

## 공통 안전 정책

Configuration Apply는 운영 DB 설정을 변경하는 기능이므로 다음 정책을 적용합니다.

```text
Profile에 등록된 Parameter만 변경 가능
dynamic = true 인 Parameter만 변경 가능
applyAllowed = true 인 Parameter만 변경 가능
ParameterValueType에 따라 targetValue 검증
동일 DB에 REQUESTED 또는 RUNNING Apply가 있으면 새 Apply 차단
SET GLOBAL 실행 후 반드시 변경 후 Snapshot으로 재검증
```

---

## 상태 정의

### OperationJob 상태

Apply 요청은 OperationJob으로 생성됩니다.

```text
QUEUED
RUNNING
SUCCEEDED
FAILED
CANCELLED
TIMED_OUT
```

### ConfigurationApply 상태

ConfigurationApply는 설정 변경 도메인 관점의 결과 상태입니다.

```text
REQUESTED
RUNNING
SUCCEEDED
PARTIALLY_SUCCEEDED
FAILED
CANCELLED
```

### ConfigurationApplyItem 상태

각 Parameter 단위 변경 결과는 Item 상태로 관리합니다.

```text
PENDING
APPLIED
VERIFIED
SKIPPED
FAILED
UNSUPPORTED
```

상태 의미는 다음과 같습니다.

| 상태 | 의미 |
|---|---|
| PENDING | 아직 적용 전 |
| APPLIED | `SET GLOBAL` 실행은 성공했지만 검증 전 |
| VERIFIED | 변경 후 실제값이 요청값과 일치함 |
| SKIPPED | 정책상 건너뜀 |
| FAILED | 적용 또는 검증 실패 |
| UNSUPPORTED | Static Parameter 등 현재 MVP에서 지원하지 않음 |

---

# 1. Configuration Apply Job 생성

## Request

```http
POST /api/v1/database-instances/{databaseId}/operations/configuration-applies
```

## Headers

```http
Content-Type: application/json
Idempotency-Key: idem-config-apply-001
```

`Idempotency-Key`는 선택값입니다.

같은 `databaseId`, `jobType`, `idempotencyKey`로 이미 생성된 Job이 있으면 새 Job을 만들지 않고 기존 Job을 반환합니다.

---

## Request Body

```json
{
  "profileId": 1,
  "requestedBy": "local-user",
  "reason": "enable slow query log for performance investigation",
  "parameters": [
    {
      "parameterName": "slow_query_log",
      "targetValue": "ON"
    },
    {
      "parameterName": "long_query_time",
      "targetValue": "1.0"
    }
  ]
}
```

---

## Request Field 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| profileId | number | Y | 적용 기준으로 사용할 Configuration Profile ID |
| requestedBy | string | Y | 요청자 |
| reason | string | N | 변경 사유 |
| parameters | array | Y | 변경할 Parameter 목록 |
| parameters[].parameterName | string | Y | 변경할 설정명 |
| parameters[].targetValue | string | Y | 변경할 목표값 |

---

## Response

```json
{
  "jobId": 31,
  "jobType": "CONFIGURATION_APPLY",
  "targetDatabaseId": 1,
  "status": "QUEUED",
  "requestedBy": "local-user",
  "idempotencyKey": "idem-config-apply-001",
  "requestPayload": "{\"profileId\":1,\"reason\":\"enable slow query log for performance investigation\",\"requestedBy\":\"local-user\",\"parameters\":[{\"parameterName\":\"slow_query_log\",\"targetValue\":\"ON\"},{\"parameterName\":\"long_query_time\",\"targetValue\":\"1.0\"}]}"
}
```

실제 응답 필드는 현재 `OperationJobResponse` 구현 기준을 따릅니다.

---

## 설명

이 API는 MySQL 설정을 즉시 변경하지 않습니다.

요청을 검증한 뒤 `CONFIGURATION_APPLY` Job을 생성하고 `QUEUED` 상태로 저장합니다.

실제 설정 변경은 Worker가 Job을 Claim한 뒤 실행합니다.

```text
POST Apply 요청
  ↓
OperationJob 생성
  ↓
status = QUEUED
  ↓
응답으로 Job ID 반환
```

---

## curl 예시

```bash
curl -X POST http://localhost:8080/api/v1/database-instances/1/operations/configuration-applies \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: idem-config-apply-001" \
  -d '{
    "profileId": 1,
    "requestedBy": "local-user",
    "reason": "enable slow query log for performance investigation",
    "parameters": [
      {
        "parameterName": "slow_query_log",
        "targetValue": "ON"
      },
      {
        "parameterName": "long_query_time",
        "targetValue": "1.0"
      }
    ]
  }'
```

---

# 2. Configuration Apply Job 실행

## Request

```http
POST /api/v1/operation-jobs/claim?workerId=worker-1
```

정확한 경로는 현재 Operation Job Claim Controller 매핑을 따릅니다.

---

## 설명

`CONFIGURATION_APPLY` Job은 Worker가 Claim할 때 실행됩니다.

실행 흐름은 다음과 같습니다.

```text
QUEUED Job 조회
  ↓
Worker가 Job Claim
  ↓
JobStatus = RUNNING
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
변경 전 Snapshot 저장
  ↓
beforeValue 기록
  ↓
DatabaseConfigurationApplyPort 선택
  ↓
MySQL SET GLOBAL 실행
  ↓
변경 후 Snapshot 저장
  ↓
afterValue 검증
  ↓
ConfigurationApply 완료 처리
  ↓
OperationJob 성공/실패 처리
```

---

## 성공 기준

`SET GLOBAL` 명령이 성공했다고 바로 성공으로 보지 않습니다.

성공 조건은 다음과 같습니다.

```text
SET GLOBAL 실행 성공
AND
변경 후 Snapshot의 actualValue가 requestedValue와 일치
```

예시:

```text
requestedValue = ON
afterValue     = 1
valueType      = BOOLEAN
결과           = VERIFIED
```

Boolean 값은 의미 기준으로 비교합니다.

```text
ON, TRUE, 1, YES, Y
  → true 계열

OFF, FALSE, 0, NO, N
  → false 계열
```

---

## 실패 기준

다음 상황에서는 Apply Item이 실패할 수 있습니다.

```text
SET GLOBAL 실행 실패
변경 후 Snapshot에 해당 parameter가 없음
변경 후 actualValue가 requestedValue와 다름
지원하지 않는 parameter
잘못된 targetValue
DB 접속 실패
```

하나 이상의 Item이 실패하면 OperationJob은 보수적으로 `FAILED` 처리합니다.

ConfigurationApply 자체는 결과에 따라 다음 중 하나가 될 수 있습니다.

```text
SUCCEEDED
PARTIALLY_SUCCEEDED
FAILED
```

---

# 3. Job 기준 Configuration Apply 결과 조회

## Request

```http
GET /api/v1/jobs/{jobId}/configuration-apply
```

---

## Response

```json
{
  "applyId": 7,
  "databaseId": 1,
  "operationJobId": 31,
  "requestedBy": "local-user",
  "reason": "enable slow query log for performance investigation",
  "status": "SUCCEEDED",
  "totalCount": 2,
  "successCount": 2,
  "failedCount": 0,
  "skippedCount": 0,
  "beforeSnapshotId": 12,
  "afterSnapshotId": 13,
  "createdAt": "2026-07-07T14:30:00",
  "startedAt": "2026-07-07T14:30:01",
  "completedAt": "2026-07-07T14:30:05",
  "items": [
    {
      "applyItemId": 1,
      "applyId": 7,
      "parameterName": "slow_query_log",
      "requestedValue": "ON",
      "beforeValue": "OFF",
      "afterValue": "ON",
      "valueType": "BOOLEAN",
      "dynamic": true,
      "applyAllowed": true,
      "applyStatus": "VERIFIED",
      "failureCode": null,
      "failureMessage": null,
      "createdAt": "2026-07-07T14:30:00",
      "appliedAt": "2026-07-07T14:30:02",
      "verifiedAt": "2026-07-07T14:30:05"
    },
    {
      "applyItemId": 2,
      "applyId": 7,
      "parameterName": "long_query_time",
      "requestedValue": "1.0",
      "beforeValue": "10.000000",
      "afterValue": "1.000000",
      "valueType": "NUMBER",
      "dynamic": true,
      "applyAllowed": true,
      "applyStatus": "VERIFIED",
      "failureCode": null,
      "failureMessage": null,
      "createdAt": "2026-07-07T14:30:00",
      "appliedAt": "2026-07-07T14:30:03",
      "verifiedAt": "2026-07-07T14:30:05"
    }
  ]
}
```

---

## 설명

Job 목록 화면에서 특정 `CONFIGURATION_APPLY` Job의 상세 결과를 조회할 때 사용하는 API입니다.

운영 콘솔에서는 보통 Job 목록에서 상세 화면으로 이동하기 때문에 `jobId` 기준 조회가 가장 자주 사용됩니다.

```text
OperationJob
  ↓ operationJobId
ConfigurationApply
  ↓ applyId
ConfigurationApplyItem 목록
```

---

## curl 예시

```bash
curl http://localhost:8080/api/v1/jobs/31/configuration-apply
```

---

# 4. Apply ID 기준 Configuration Apply 결과 조회

## Request

```http
GET /api/v1/configuration-applies/{applyId}
```

---

## Response

```json
{
  "applyId": 7,
  "databaseId": 1,
  "operationJobId": 31,
  "requestedBy": "local-user",
  "reason": "enable slow query log for performance investigation",
  "status": "PARTIALLY_SUCCEEDED",
  "totalCount": 3,
  "successCount": 2,
  "failedCount": 1,
  "skippedCount": 0,
  "beforeSnapshotId": 12,
  "afterSnapshotId": 13,
  "createdAt": "2026-07-07T14:30:00",
  "startedAt": "2026-07-07T14:30:01",
  "completedAt": "2026-07-07T14:30:05",
  "items": [
    {
      "applyItemId": 1,
      "applyId": 7,
      "parameterName": "slow_query_log",
      "requestedValue": "ON",
      "beforeValue": "OFF",
      "afterValue": "ON",
      "valueType": "BOOLEAN",
      "dynamic": true,
      "applyAllowed": true,
      "applyStatus": "VERIFIED",
      "failureCode": null,
      "failureMessage": null,
      "createdAt": "2026-07-07T14:30:00",
      "appliedAt": "2026-07-07T14:30:02",
      "verifiedAt": "2026-07-07T14:30:05"
    },
    {
      "applyItemId": 2,
      "applyId": 7,
      "parameterName": "long_query_time",
      "requestedValue": "1.0",
      "beforeValue": "10.000000",
      "afterValue": "1.000000",
      "valueType": "NUMBER",
      "dynamic": true,
      "applyAllowed": true,
      "applyStatus": "VERIFIED",
      "failureCode": null,
      "failureMessage": null,
      "createdAt": "2026-07-07T14:30:00",
      "appliedAt": "2026-07-07T14:30:03",
      "verifiedAt": "2026-07-07T14:30:05"
    },
    {
      "applyItemId": 3,
      "applyId": 7,
      "parameterName": "max_connections",
      "requestedValue": "500",
      "beforeValue": "151",
      "afterValue": "151",
      "valueType": "NUMBER",
      "dynamic": true,
      "applyAllowed": true,
      "applyStatus": "FAILED",
      "failureCode": "VERIFY_VALUE_MISMATCH",
      "failureMessage": "Requested value does not match after value. requestedValue=500, afterValue=151",
      "createdAt": "2026-07-07T14:30:00",
      "appliedAt": "2026-07-07T14:30:03",
      "verifiedAt": null
    }
  ]
}
```

---

## 설명

Apply 도메인 자체를 기준으로 상세 결과를 조회하는 API입니다.

운영자가 특정 Apply 결과를 직접 확인하거나, Audit 또는 Alert에서 Apply ID를 기준으로 연결할 때 사용할 수 있습니다.

---

## curl 예시

```bash
curl http://localhost:8080/api/v1/configuration-applies/7
```

---

# 5. 검증 정책

Configuration Apply Job 생성 전에는 다음 검증을 수행합니다.

## 5.1 profileId 필수

`profileId`가 없으면 요청을 거부합니다.

```json
{
  "requestedBy": "local-user",
  "parameters": [
    {
      "parameterName": "slow_query_log",
      "targetValue": "ON"
    }
  ]
}
```

결과:

```text
profileId is required.
```

---

## 5.2 requestedBy 필수

요청자는 필수입니다.

```json
{
  "profileId": 1,
  "requestedBy": "",
  "parameters": [
    {
      "parameterName": "slow_query_log",
      "targetValue": "ON"
    }
  ]
}
```

결과:

```text
requestedBy is required.
```

---

## 5.3 parameters 필수

변경할 Parameter가 없으면 요청을 거부합니다.

```json
{
  "profileId": 1,
  "requestedBy": "local-user",
  "parameters": []
}
```

결과:

```text
parameters is required.
```

---

## 5.4 중복 Parameter 차단

동일 요청 안에 같은 Parameter를 중복으로 넣을 수 없습니다.

대소문자는 구분하지 않습니다.

```json
{
  "profileId": 1,
  "requestedBy": "local-user",
  "parameters": [
    {
      "parameterName": "slow_query_log",
      "targetValue": "ON"
    },
    {
      "parameterName": "SLOW_QUERY_LOG",
      "targetValue": "OFF"
    }
  ]
}
```

결과:

```text
Duplicate parameterName is not allowed.
```

---

## 5.5 Profile에 없는 Parameter 차단

요청한 Parameter는 반드시 해당 Configuration Profile에 등록되어 있어야 합니다.

```text
Configuration profile parameter not found.
```

이 정책을 둔 이유는 사용자가 임의의 MySQL 설정명을 넣어 변경하는 것을 막기 위해서입니다.

---

## 5.6 Static Parameter 차단

`dynamic = false`인 Parameter는 Phase 7 MVP에서 적용하지 않습니다.

```text
Static parameter is not supported in configuration apply MVP.
```

Static Parameter를 적용하려면 설정 파일 수정, 문법 검증, DB 재시작, 실패 시 복구 절차가 필요하므로 후속 Phase로 분리합니다.

---

## 5.7 applyAllowed = false 차단

`applyAllowed = false`인 Parameter는 플랫폼에서 변경하지 않습니다.

```text
Parameter is not allowed to be applied by platform.
```

`dynamic = true`이더라도 운영 정책상 플랫폼 변경을 막을 수 있습니다.

---

## 5.8 BOOLEAN 값 검증

BOOLEAN 타입은 다음 값을 허용합니다.

True 계열:

```text
ON
TRUE
1
YES
Y
```

False 계열:

```text
OFF
FALSE
0
NO
N
```

허용되지 않는 값은 거부합니다.

```text
Invalid BOOLEAN targetValue.
```

---

## 5.9 NUMBER 값 검증

NUMBER 타입은 숫자로 파싱 가능해야 합니다.

허용 예시:

```text
1
1.0
300
0.5
```

거부 예시:

```text
abc
10sec
one
```

결과:

```text
Invalid NUMBER targetValue.
```

---

## 5.10 STRING 값 검증

STRING 타입은 위험한 SQL 문자를 제한합니다.

현재 차단하는 패턴은 다음과 같습니다.

```text
;
--
/*
*/
```

결과:

```text
Unsafe STRING targetValue.
```

---

# 6. MySQL SET GLOBAL 적용 정책

MySQL 설정 변경은 `DatabaseConfigurationApplyPort`를 통해 실행합니다.

현재 MySQL Adapter는 다음 Parameter만 허용합니다.

```text
slow_query_log
long_query_time
max_connections
```

사용 SQL 예시는 다음과 같습니다.

```sql
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1.0;
SET GLOBAL max_connections = 500;
```

Parameter 이름은 PreparedStatement 바인딩이 어렵기 때문에 whitelist 기반으로 방어합니다.

값은 `ParameterValueType`에 따라 정규화합니다.

---

## BOOLEAN 정규화

입력값:

```text
true
1
YES
```

정규화 결과:

```text
ON
```

입력값:

```text
false
0
NO
```

정규화 결과:

```text
OFF
```

---

## NUMBER 정규화

입력값:

```text
1.000000
```

정규화 결과:

```text
1
```

입력값:

```text
0.5000
```

정규화 결과:

```text
0.5
```

---

## STRING 정규화

문자열은 앞뒤 공백을 제거하고, SQL 위험 패턴을 차단합니다.

문자열 값은 SQL literal로 사용할 때 quote escaping을 수행합니다.

---

# 7. 변경 전 Snapshot

설정 변경 전에 반드시 Snapshot을 저장합니다.

```text
beforeSnapshotId
```

변경 전 Snapshot을 저장하는 이유는 다음과 같습니다.

```text
변경 전 값 확인
변경 이력 추적
장애 발생 시 원인 분석
수동 Rollback 기준 확보
Audit 근거 확보
```

예시:

```text
slow_query_log = OFF
```

이후 ApplyItem에는 다음처럼 저장됩니다.

```json
{
  "parameterName": "slow_query_log",
  "requestedValue": "ON",
  "beforeValue": "OFF"
}
```

---

# 8. 변경 후 Snapshot

`SET GLOBAL` 실행 후 다시 Snapshot을 저장합니다.

```text
afterSnapshotId
```

변경 후 Snapshot을 저장하는 이유는 다음과 같습니다.

```text
명령 실행 결과 확인
실제 반영값 검증
DBMS 값 정규화 확인
동시 변경 감지
Audit 근거 확보
```

예시:

```text
slow_query_log = ON
```

이후 ApplyItem에는 다음처럼 저장됩니다.

```json
{
  "parameterName": "slow_query_log",
  "requestedValue": "ON",
  "beforeValue": "OFF",
  "afterValue": "ON",
  "applyStatus": "VERIFIED"
}
```

---

# 9. 실패 응답 예시

현재 별도 Error Response DTO를 정의하지 않았다면 Spring 기본 오류 응답을 따릅니다.

예시:

```json
{
  "timestamp": "2026-07-07T14:30:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Static parameter is not supported in configuration apply MVP. parameterName=innodb_log_file_size",
  "path": "/api/v1/database-instances/1/operations/configuration-applies"
}
```

후속 개선으로 공통 Error Response를 둘 수 있습니다.

예상 구조:

```json
{
  "code": "CONFIGURATION_APPLY_VALIDATION_FAILED",
  "message": "Static parameter is not supported in configuration apply MVP.",
  "details": {
    "parameterName": "innodb_log_file_size"
  }
}
```

---

# 10. API 사용 흐름 예시

```text
1. Configuration Profile 생성
  ↓
2. Profile Parameter 등록
  ↓
3. Configuration Apply Job 생성
  ↓
4. Worker Claim
  ↓
5. Apply 실행
  ↓
6. Job 기준 Apply 결과 조회
```

---

## 10.1 Profile 생성

```bash
curl -X POST http://localhost:8080/api/v1/configuration-profiles \
  -H "Content-Type: application/json" \
  -d '{
    "profileName": "mysql-production-standard",
    "engineType": "MYSQL",
    "environment": "PRODUCTION",
    "versionRange": ">=8.0",
    "description": "MySQL production baseline profile"
  }'
```

---

## 10.2 Parameter 추가

```bash
curl -X POST http://localhost:8080/api/v1/configuration-profiles/1/parameters \
  -H "Content-Type: application/json" \
  -d '{
    "parameterName": "slow_query_log",
    "expectedValue": "ON",
    "valueType": "BOOLEAN",
    "required": true,
    "dynamic": true,
    "applyAllowed": true,
    "description": "Production DB should enable slow query log."
  }'
```

```bash
curl -X POST http://localhost:8080/api/v1/configuration-profiles/1/parameters \
  -H "Content-Type: application/json" \
  -d '{
    "parameterName": "long_query_time",
    "expectedValue": "1.0",
    "valueType": "NUMBER",
    "required": true,
    "dynamic": true,
    "applyAllowed": true,
    "description": "Slow query threshold should be 1 second."
  }'
```

---

## 10.3 Configuration Apply Job 생성

```bash
curl -X POST http://localhost:8080/api/v1/database-instances/1/operations/configuration-applies \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: idem-config-apply-001" \
  -d '{
    "profileId": 1,
    "requestedBy": "local-user",
    "reason": "enable slow query log for performance investigation",
    "parameters": [
      {
        "parameterName": "slow_query_log",
        "targetValue": "ON"
      },
      {
        "parameterName": "long_query_time",
        "targetValue": "1.0"
      }
    ]
  }'
```

---

## 10.4 Worker Claim

```bash
curl -X POST "http://localhost:8080/api/v1/operation-jobs/claim?workerId=worker-1"
```

---

## 10.5 Job 기준 결과 조회

```bash
curl http://localhost:8080/api/v1/jobs/31/configuration-apply
```

---

## 10.6 Apply ID 기준 결과 조회

```bash
curl http://localhost:8080/api/v1/configuration-applies/7
```

---

# 11. 설계 메모

## 11.1 설정 변경을 즉시 실행하지 않는 이유

설정 변경은 단순 CRUD가 아니라 운영 작업입니다.

즉시 실행 방식은 다음 문제가 있습니다.

```text
HTTP timeout 가능성
실패 상태 추적 어려움
변경 전 값 누락 가능성
변경 후 검증 누락 가능성
Audit 누락 가능성
동시 변경 제어 어려움
```

그래서 Apply 요청은 반드시 OperationJob으로 생성합니다.

---

## 11.2 Dynamic Parameter만 지원하는 이유

Phase 7 MVP에서는 `SET GLOBAL`로 변경 가능한 Dynamic Parameter만 지원합니다.

Static Parameter는 설정 파일 수정과 DB 재시작이 필요할 수 있습니다.

Static Parameter까지 지원하려면 다음 기능이 필요합니다.

```text
my.cnf 위치 탐지
설정 파일 수정
문법 검증
DB 재시작
재시작 실패 시 복구
Agent 기반 파일 작업
승인 Workflow
```

따라서 Static Parameter는 후속 Phase에서 다루는 것이 맞습니다.

---

## 11.3 변경 후 재검증을 하는 이유

`SET GLOBAL` 실행 성공만으로는 실제 반영 여부를 보장할 수 없습니다.

DBMS가 값을 정규화할 수 있고, 일부 값은 권한이나 환경에 따라 반영되지 않을 수 있습니다.

따라서 변경 후 Snapshot을 다시 수집하고 `requestedValue`와 `afterValue`를 비교합니다.

---

## 11.4 Job 상태와 Apply 상태를 분리한 이유

OperationJob은 작업 실행 상태를 표현합니다.

ConfigurationApply는 설정 변경 결과를 표현합니다.

예를 들어 일부 Parameter만 실패한 경우 다음처럼 표현할 수 있습니다.

```text
OperationJob.status = FAILED
ConfigurationApply.status = PARTIALLY_SUCCEEDED
```

이렇게 하면 Job Engine은 보수적으로 유지하면서, 실제 변경 결과는 Apply 도메인에서 자세히 확인할 수 있습니다.