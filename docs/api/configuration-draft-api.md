# Configuration Drift API

## 개요

Configuration Drift API는 DB FleetOps에 등록된 DB 인스턴스의 실제 설정값과 표준 Configuration Profile을 비교하고, 그 결과를 조회하기 위한 API입니다.

이 기능은 DB 설정이 운영 기준과 다르게 변경되었는지 확인하기 위해 사용합니다.

현재 지원 DBMS

- MySQL

---

## 주요 개념

### Configuration Profile

운영자가 기대하는 DB 설정 기준입니다.

예시:

```json
{
  "slow_query_log": "ON",
  "long_query_time": "1.0",
  "binlog_format": "ROW"
}
```

### Configuration Snapshot

특정 시점에 실제 DB에서 수집한 설정값입니다.

MySQL의 경우 `SHOW GLOBAL VARIABLES` 결과를 저장합니다.

### Configuration Drift

Profile의 기대값과 Snapshot의 실제값을 비교한 결과입니다.

비교 결과는 다음 상태로 구분합니다.

- `COMPLIANT`: 기대값과 실제값이 일치함
- `NON_COMPLIANT`: 실제값이 존재하지만 기대값과 다름
- `MISSING`: Profile에는 있으나 Snapshot에는 해당 설정값이 없음

---

# 1. Configuration Profile 생성

## Request

```http
POST /api/v1/configuration-profiles
```

### Request Body

```json
{
  "profileName": "mysql-production-standard",
  "engineType": "MYSQL",
  "environment": "PRODUCTION",
  "versionRange": ">=8.0",
  "description": "MySQL production baseline profile"
}
```

---

## Response

```json
{
  "profileId": 1,
  "profileName": "mysql-production-standard",
  "engineType": "MYSQL",
  "environment": "PRODUCTION",
  "versionRange": ">=8.0",
  "description": "MySQL production baseline profile",
  "status": "DRAFT",
  "parameters": []
}
```

---

# 2. Configuration Profile 목록 조회

## Request

```http
GET /api/v1/configuration-profiles
```

MySQL Profile만 조회할 경우:

```http
GET /api/v1/configuration-profiles?engineType=MYSQL
```

---

## Response

```json
[
  {
    "profileId": 1,
    "profileName": "mysql-production-standard",
    "engineType": "MYSQL",
    "environment": "PRODUCTION",
    "versionRange": ">=8.0",
    "description": "MySQL production baseline profile",
    "status": "DRAFT",
    "parameters": []
  }
]
```

---

# 3. Configuration Profile 상세 조회

## Request

```http
GET /api/v1/configuration-profiles/{profileId}
```

---

## Response

```json
{
  "profileId": 1,
  "profileName": "mysql-production-standard",
  "engineType": "MYSQL",
  "environment": "PRODUCTION",
  "versionRange": ">=8.0",
  "description": "MySQL production baseline profile",
  "status": "ACTIVE",
  "parameters": [
    {
      "parameterId": 1,
      "profileId": 1,
      "parameterName": "slow_query_log",
      "expectedValue": "ON",
      "valueType": "BOOLEAN",
      "required": true,
      "dynamic": true,
      "applyAllowed": true,
      "description": "Production DB should enable slow query log."
    }
  ]
}
```

---

# 4. Configuration Profile 활성화

## Request

```http
POST /api/v1/configuration-profiles/{profileId}/activate
```

---

## Response

```json
{
  "profileId": 1,
  "profileName": "mysql-production-standard",
  "engineType": "MYSQL",
  "environment": "PRODUCTION",
  "versionRange": ">=8.0",
  "description": "MySQL production baseline profile",
  "status": "ACTIVE",
  "parameters": []
}
```

---

# 5. Configuration Profile 비활성화

## Request

```http
POST /api/v1/configuration-profiles/{profileId}/deactivate
```

---

## Response

```json
{
  "profileId": 1,
  "profileName": "mysql-production-standard",
  "engineType": "MYSQL",
  "environment": "PRODUCTION",
  "versionRange": ">=8.0",
  "description": "MySQL production baseline profile",
  "status": "INACTIVE",
  "parameters": []
}
```

---

# 6. Configuration Profile Parameter 추가

## Request

```http
POST /api/v1/configuration-profiles/{profileId}/parameters
```

### Request Body

```json
{
  "parameterName": "slow_query_log",
  "expectedValue": "ON",
  "valueType": "BOOLEAN",
  "required": true,
  "dynamic": true,
  "applyAllowed": true,
  "description": "Production DB should enable slow query log."
}
```

---

## Response

```json
{
  "parameterId": 1,
  "profileId": 1,
  "parameterName": "slow_query_log",
  "expectedValue": "ON",
  "valueType": "BOOLEAN",
  "required": true,
  "dynamic": true,
  "applyAllowed": true,
  "description": "Production DB should enable slow query log."
}
```

---

# 7. Configuration Profile Parameter 목록 조회

## Request

```http
GET /api/v1/configuration-profiles/{profileId}/parameters
```

---

## Response

```json
[
  {
    "parameterId": 1,
    "profileId": 1,
    "parameterName": "slow_query_log",
    "expectedValue": "ON",
    "valueType": "BOOLEAN",
    "required": true,
    "dynamic": true,
    "applyAllowed": true,
    "description": "Production DB should enable slow query log."
  },
  {
    "parameterId": 2,
    "profileId": 1,
    "parameterName": "long_query_time",
    "expectedValue": "1.0",
    "valueType": "NUMBER",
    "required": true,
    "dynamic": true,
    "applyAllowed": true,
    "description": "Slow query threshold should be 1 second."
  }
]
```

---

# 8. Configuration Check Job 생성

## Request

```http
POST /api/v1/database-instances/{databaseId}/operations/configuration-checks
```

### Request Header

```http
Idempotency-Key: config-check-db-1-profile-1-20260707
```

### Request Body

```json
{
  "profileId": 1,
  "requestedBy": "local-user",
  "reason": "daily configuration compliance check"
}
```

---

## Response

```json
{
  "jobId": 10,
  "jobType": "CONFIGURATION_CHECK",
  "targetDatabaseId": 1,
  "status": "QUEUED",
  "requestedBy": "local-user",
  "idempotencyKey": "config-check-db-1-profile-1-20260707"
}
```

---

## 처리 과정

```text
Configuration Check Job 생성 요청
      │
      ▼
ManagedDatabase 조회
      │
      ▼
DB 활성 상태 확인
      │
      ▼
Idempotency-Key 중복 확인
      │
      ▼
OperationJob 생성
      │
      ▼
JobType = CONFIGURATION_CHECK
      │
      ▼
status = QUEUED
```

---

# 9. Configuration Check Job 실행

Configuration Check Job은 Worker가 claim할 때 실행됩니다.

## Request

```http
POST /api/v1/operation-jobs/claim?workerId=worker-1
```

정확한 경로는 Operation Job Claim API Controller의 매핑 기준을 따릅니다.

---

## 처리 과정

```text
QUEUED Job 조회
      │
      ▼
Worker가 Job Claim
      │
      ▼
JobStatus = RUNNING
      │
      ▼
payloadJson에서 profileId 추출
      │
      ▼
ManagedDatabase 조회
      │
      ▼
DBMS Engine 확인
      │
      ▼
SHOW GLOBAL VARIABLES 실행
      │
      ▼
ConfigurationSnapshot 저장
      │
      ▼
ConfigurationSnapshotItem 저장
      │
      ▼
Profile Parameter와 Snapshot Item 비교
      │
      ▼
ConfigurationDrift 저장
      │
      ▼
ConfigurationDriftItem 저장
      │
      ▼
JobStatus = SUCCEEDED
```

---

## 성공 시 결과 예시

```json
{
  "jobId": 10,
  "jobType": "CONFIGURATION_CHECK",
  "targetDatabaseId": 1,
  "status": "SUCCEEDED",
  "resultMessage": "Configuration check completed. driftId=5, status=NON_COMPLIANT"
}
```

---

## 실패 시 처리

다음 상황에서는 Job이 실패할 수 있습니다.

- payloadJson이 비어 있음
- profileId가 없음
- databaseId에 해당하는 DB가 없음
- DB 접속 실패
- `SHOW GLOBAL VARIABLES` 실행 실패
- Profile이 존재하지 않음
- Profile과 Snapshot의 engineType이 다름
- Drift 저장 실패

실패 시 retry 가능한 경우 Job은 다시 `QUEUED` 상태로 전환됩니다.

---

# 10. 최신 Configuration Drift 조회

## Request

```http
GET /api/v1/database-instances/{databaseId}/configuration-drifts/latest
```

---

## Response

```json
{
  "driftId": 5,
  "databaseId": 1,
  "profileId": 1,
  "snapshotId": 3,
  "engineType": "MYSQL",
  "status": "NON_COMPLIANT",
  "totalCount": 3,
  "compliantCount": 1,
  "nonCompliantCount": 1,
  "missingCount": 1,
  "checkedAt": "2026-07-07T14:30:00",
  "items": [
    {
      "driftItemId": 1,
      "driftId": 5,
      "parameterName": "slow_query_log",
      "expectedValue": "ON",
      "actualValue": "1",
      "valueType": "BOOLEAN",
      "required": true,
      "dynamic": true,
      "applyAllowed": true,
      "complianceStatus": "COMPLIANT",
      "message": "Expected value matches actual value.",
      "createdAt": "2026-07-07T14:30:00"
    },
    {
      "driftItemId": 2,
      "driftId": 5,
      "parameterName": "long_query_time",
      "expectedValue": "1.0",
      "actualValue": "10.000000",
      "valueType": "NUMBER",
      "required": true,
      "dynamic": true,
      "applyAllowed": true,
      "complianceStatus": "NON_COMPLIANT",
      "message": "Expected 1.0 but actual value is 10.000000.",
      "createdAt": "2026-07-07T14:30:00"
    },
    {
      "driftItemId": 3,
      "driftId": 5,
      "parameterName": "binlog_format",
      "expectedValue": "ROW",
      "actualValue": null,
      "valueType": "STRING",
      "required": true,
      "dynamic": false,
      "applyAllowed": false,
      "complianceStatus": "MISSING",
      "message": "Expected parameter is missing from actual snapshot.",
      "createdAt": "2026-07-07T14:30:00"
    }
  ]
}
```

---

# 11. DB별 Configuration Drift 이력 조회

## Request

```http
GET /api/v1/database-instances/{databaseId}/configuration-drifts
```

---

## Response

목록 조회에서는 DriftItem을 포함하지 않고 요약 정보만 반환합니다.

```json
[
  {
    "driftId": 5,
    "databaseId": 1,
    "profileId": 1,
    "snapshotId": 3,
    "engineType": "MYSQL",
    "status": "NON_COMPLIANT",
    "totalCount": 3,
    "compliantCount": 1,
    "nonCompliantCount": 1,
    "missingCount": 1,
    "checkedAt": "2026-07-07T14:30:00",
    "items": []
  },
  {
    "driftId": 4,
    "databaseId": 1,
    "profileId": 1,
    "snapshotId": 2,
    "engineType": "MYSQL",
    "status": "COMPLIANT",
    "totalCount": 3,
    "compliantCount": 3,
    "nonCompliantCount": 0,
    "missingCount": 0,
    "checkedAt": "2026-07-07T13:30:00",
    "items": []
  }
]
```

---

# 12. Configuration Drift 상세 조회

## Request

```http
GET /api/v1/configuration-drifts/{driftId}
```

---

## Response

```json
{
  "driftId": 5,
  "databaseId": 1,
  "profileId": 1,
  "snapshotId": 3,
  "engineType": "MYSQL",
  "status": "NON_COMPLIANT",
  "totalCount": 3,
  "compliantCount": 1,
  "nonCompliantCount": 1,
  "missingCount": 1,
  "checkedAt": "2026-07-07T14:30:00",
  "items": [
    {
      "driftItemId": 1,
      "driftId": 5,
      "parameterName": "slow_query_log",
      "expectedValue": "ON",
      "actualValue": "1",
      "valueType": "BOOLEAN",
      "required": true,
      "dynamic": true,
      "applyAllowed": true,
      "complianceStatus": "COMPLIANT",
      "message": "Expected value matches actual value.",
      "createdAt": "2026-07-07T14:30:00"
    },
    {
      "driftItemId": 2,
      "driftId": 5,
      "parameterName": "long_query_time",
      "expectedValue": "1.0",
      "actualValue": "10.000000",
      "valueType": "NUMBER",
      "required": true,
      "dynamic": true,
      "applyAllowed": true,
      "complianceStatus": "NON_COMPLIANT",
      "message": "Expected 1.0 but actual value is 10.000000.",
      "createdAt": "2026-07-07T14:30:00"
    }
  ]
}
```

---

# 13. 비교 규칙

Configuration Drift 비교는 `ParameterValueType`에 따라 다르게 수행합니다.

## STRING

문자열은 앞뒤 공백을 제거하고 대소문자를 정규화한 뒤 비교합니다.

```text
expectedValue = ROW
actualValue   = row
결과           = COMPLIANT
```

## NUMBER

숫자는 문자열이 아니라 숫자 의미 기준으로 비교합니다.

```text
expectedValue = 1.0
actualValue   = 1.000000
결과           = COMPLIANT
```

```text
expectedValue = 1.0
actualValue   = 10.000000
결과           = NON_COMPLIANT
```

## BOOLEAN

Boolean 값은 의미가 같은 표현을 동일하게 봅니다.

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

예시:

```text
expectedValue = ON
actualValue   = 1
결과           = COMPLIANT
```

---

# 14. 상태 정의

## Drift 전체 상태

| 상태 | 의미 |
|---|---|
| COMPLIANT | 모든 항목이 기준과 일치함 |
| NON_COMPLIANT | 하나 이상의 항목이 불일치하거나 누락됨 |

## Drift Item 상태

| 상태 | 의미 |
|---|---|
| COMPLIANT | 기대값과 실제값이 일치함 |
| NON_COMPLIANT | 실제값은 있으나 기대값과 다름 |
| MISSING | Profile에는 있으나 Snapshot에는 해당 항목이 없음 |

---

# 15. 설계 메모

## Configuration Check를 OperationJob으로 처리하는 이유

Configuration Check는 단순 조회처럼 보이지만 실제로는 여러 단계를 수행합니다.

```text
DB 접속
      │
      ▼
설정값 수집
      │
      ▼
Snapshot 저장
      │
      ▼
Profile 비교
      │
      ▼
Drift 저장
```

이 과정 중 DB 접속 실패, Profile 오류, 저장 실패 등이 발생할 수 있으므로 OperationJob으로 관리합니다.

---

## Snapshot과 Drift를 분리한 이유

Snapshot은 특정 시점의 실제 설정값입니다.

Drift는 Profile과 Snapshot을 비교한 결과입니다.

```text
ConfigurationSnapshot
  - 실제 DB 설정값 수집 결과

ConfigurationDrift
  - 기대값과 실제값의 비교 결과
```

둘을 분리하면 나중에 같은 Snapshot을 다른 Profile과 비교하거나, 수집 이력과 판단 이력을 따로 추적할 수 있습니다.

---

## 목록 조회에서 Item을 제외한 이유

Drift 목록 조회에서 모든 DriftItem을 포함하면 응답이 커질 수 있습니다.

예를 들어 Drift 10개에 각 항목이 100개라면 한 번의 목록 조회에서 1,000개 항목이 반환됩니다.

그래서 목록 API는 요약만 반환하고, 상세 API에서만 DriftItem을 반환합니다.

---

# 16. 사용 흐름 예시

```text
1. DB 등록
      │
      ▼
2. Configuration Profile 생성
      │
      ▼
3. Profile Parameter 등록
      │
      ▼
4. Configuration Check Job 생성
      │
      ▼
5. Worker가 Job 실행
      │
      ▼
6. Drift 결과 저장
      │
      ▼
7. Latest Drift API 조회
```

---

# 17. curl 예시

## Profile 생성

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

## Parameter 추가

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

## Configuration Check Job 생성

```bash
curl -X POST http://localhost:8080/api/v1/database-instances/1/operations/configuration-checks \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: config-check-db-1-profile-1-20260707" \
  -d '{
    "profileId": 1,
    "requestedBy": "local-user",
    "reason": "daily configuration compliance check"
  }'
```

## Worker Claim

```bash
curl -X POST "http://localhost:8080/api/v1/operation-jobs/claim?workerId=worker-1"
```

## 최신 Drift 조회

```bash
curl http://localhost:8080/api/v1/database-instances/1/configuration-drifts/latest
```

## Drift 상세 조회

```bash
curl http://localhost:8080/api/v1/configuration-drifts/5
```