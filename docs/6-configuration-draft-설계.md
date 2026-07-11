# Configuration Drift 설계

# 1. 왜 Configuration Drift 기능이 필요한가

DB FleetOps는 여러 DB 인스턴스를 등록하고 상태를 점검하는 것에서 끝나지 않고, 운영 기준에 맞게 DB가 유지되고 있는지 확인하는 것을 목표로 합니다.

DB 운영에서는 접속 가능 여부만으로는 충분하지 않습니다. DB가 정상적으로 연결되더라도 실제 운영 설정이 기준과 다르면 성능 저하, 장애 분석 지연, 복구 실패 같은 문제가 발생할 수 있습니다.

예를 들어 MySQL에서 `slow_query_log`가 꺼져 있으면 장애 상황에서 느린 쿼리를 추적하기 어렵습니다. `long_query_time`이 너무 크게 설정되어 있으면 실제로 문제가 되는 쿼리가 로그에 남지 않을 수 있습니다. `binlog_format`이 운영 기준과 다르면 복제나 복구 정책에도 영향을 줄 수 있습니다.

문제는 이런 설정값들이 시간이 지나면서 바뀔 수 있다는 점입니다. 운영자가 직접 DB에 접속해 임시로 설정을 바꾸거나, 장애 대응 과정에서 변경한 값을 되돌리지 않거나, DBMS 버전이나 환경별로 설정 기준이 다르게 관리되면 실제 운영 상태와 문서상 기준이 달라질 수 있습니다.

이 차이를 Configuration Drift라고 봤습니다.

Configuration Drift 기능은 다음 질문에 답하기 위해 만들었습니다.

```text
이 DB는 우리가 정의한 운영 기준과 일치하는가?
일치하지 않는다면 어떤 설정이 다른가?
언제부터 달라졌는가?
다시 기준에 맞게 변경할 수 있는 항목인가?
```

단순히 현재 설정값을 보여주는 것이 아니라, 표준 기준과 실제값을 비교하고 그 결과를 이력으로 남기는 것이 중요합니다.

---

# 2. 목표

목표는 DB 설정 기준을 정의하고, 실제 DB 설정값과 비교하여 Drift를 탐지하는 구조를 만드는 것입니다.

이번에 구현한 범위는 다음과 같습니다.

```text
Configuration Profile 생성
Profile Parameter 등록
MySQL 실제 설정값 수집
Configuration Snapshot 저장
Profile과 Snapshot 비교
Configuration Drift 저장
Configuration Check Job 생성
Worker 기반 Configuration Check 실행
Configuration Drift 조회 API
```

반대로 이번에 제외한 범위도 명확히 했습니다.

```text
설정값 자동 변경
SET GLOBAL 실행
my.cnf 수정
DB 재시작
설정 변경 승인 프로세스
PostgreSQL 설정 점검
```

이렇게 범위를 나눈 이유는 Drift 탐지와 설정 변경은 위험도가 다르기 때문입니다.

Drift 탐지는 읽기 중심 작업입니다. 실제 DB 설정값을 조회하고 결과를 저장합니다. 반면 설정 변경은 운영 DB의 동작을 바꾸는 작업입니다. 잘못 적용하면 장애로 이어질 수 있으므로 별도의 검증, 승인, 재확인 구조가 필요합니다.

그래서 먼저 "기준과 실제값의 차이를 안정적으로 탐지하고 기록하는 것"에 집중했습니다. 설정 변경은 후속 작업에서 별도의 Safe Apply 구조로 다루는 것이 맞다고 판단했습니다.

---

# 3. 전체 구조

전체 흐름은 다음과 같습니다.

```text
Configuration Profile 생성
      │
      ▼
Profile Parameter 등록
      │
      ▼
Configuration Check Job 생성
      │
      ▼
Worker가 Job Claim
      │
      ▼
실제 DB 설정값 수집
      │
      ▼
Configuration Snapshot 저장
      │
      ▼
Profile Parameter와 Snapshot Item 비교
      │
      ▼
Configuration Drift 저장
      │
      ▼
Drift API 조회
```

이 흐름에서 중요한 점은 Profile, Snapshot, Drift를 하나의 테이블이나 하나의 객체로 합치지 않았다는 점입니다.

각 개념의 책임은 다릅니다.

```text
ConfigurationProfile
  - 기대하는 운영 기준

ConfigurationSnapshot
  - 특정 시점에 실제 DB에서 수집한 설정값

ConfigurationDrift
  - 기대값과 실제값을 비교한 결과
```

이렇게 분리하면 기준, 실제 수집값, 판단 결과를 각각 독립적으로 추적할 수 있습니다.

---

# 4. Desired State로서 Configuration Profile

Configuration Profile은 DB가 따라야 하는 표준 설정 기준입니다.

예를 들어 MySQL 운영 환경에서는 다음과 같은 기준을 정의할 수 있습니다.

```json
{
  "slow_query_log": "ON",
  "long_query_time": "1.0",
  "binlog_format": "ROW"
}
```

이 값들은 실제 DB에서 바로 읽은 값이 아니라, 운영자가 기대하는 상태입니다. 즉, Desired State입니다.

Kubernetes에서 manifest가 원하는 상태를 표현하고 Controller가 실제 상태와 비교하듯이, DB FleetOps에서도 Profile이 원하는 DB 설정 상태를 표현합니다.

Profile은 다음 정보를 가집니다.

```text
profileName
engineType
environment
versionRange
description
status
```

여기서 `engineType`을 둔 이유는 DBMS마다 설정 항목이 다르기 때문입니다.

MySQL의 설정 항목은 다음과 같습니다.

```text
slow_query_log
long_query_time
binlog_format
max_connections
```

PostgreSQL은 전혀 다른 이름을 사용합니다.

```text
log_min_duration_statement
shared_buffers
work_mem
max_connections
```

따라서 Profile은 반드시 어떤 DBMS 기준인지 알아야 합니다.

또한 `environment`를 둔 이유는 개발, 스테이징, 운영 환경의 기준이 다를 수 있기 때문입니다. 운영 환경에서는 slow query 기준을 엄격하게 잡을 수 있지만, 로컬 환경에서는 다르게 둘 수 있습니다.

---

# 5. Profile Parameter를 별도 Entity로 분리한 이유

처음에는 Profile 안에 JSON으로 기준값을 저장하는 방법도 생각할 수 있습니다.

예를 들어 다음과 같은 구조입니다.

```json
{
  "profileName": "mysql-production-standard",
  "parameters": {
    "slow_query_log": "ON",
    "long_query_time": "1.0",
    "binlog_format": "ROW"
  }
}
```

이 방식은 단순하지만 운영 플랫폼 관점에서는 한계가 있습니다.

첫째, 개별 Parameter에 속성을 붙이기 어렵습니다. 각 설정값이 필수인지, 동적으로 변경 가능한지, 플랫폼에서 변경 허용할 것인지 같은 정보를 표현하기 어렵습니다.

둘째, 나중에 특정 Parameter만 조회하거나 비교하거나 통계화하기 어렵습니다.

셋째, Drift 결과와 연결하기도 애매합니다. DriftItem은 결국 개별 Parameter 기준으로 발생하므로 ProfileParameter도 개별 Entity로 관리하는 편이 자연스럽습니다.

그래서 `ConfigurationProfileParameter`를 별도로 만들었습니다.

Parameter는 다음 정보를 가집니다.

```text
profileId
parameterName
expectedValue
valueType
required
dynamic
applyAllowed
description
```

여기서 중요한 필드는 `dynamic`과 `applyAllowed`입니다.

`dynamic`은 DBMS 관점의 속성입니다.

```text
이 설정값이 DB 재시작 없이 변경 가능한가?
```

`applyAllowed`는 플랫폼 정책의 속성입니다.

```text
DB FleetOps에서 이 설정값 변경을 허용할 것인가?
```

둘은 비슷해 보이지만 다릅니다.

예를 들어 어떤 설정은 MySQL에서 동적으로 변경 가능할 수 있습니다. 하지만 운영 정책상 플랫폼에서 자동 변경을 허용하지 않을 수 있습니다. 반대로 변경은 가능하더라도 운영 영향도가 크면 수동 승인 대상으로 둘 수 있습니다.

그래서 두 필드를 분리했습니다.

```text
dynamic
  - 기술적으로 즉시 변경 가능한지

applyAllowed
  - 우리 플랫폼 정책상 변경 허용 대상인지
```

이 값을 비교 결과와 DriftItem에 함께 저장합니다. 이후 Safe Configuration Apply 단계에서 이 정보를 활용할 수 있습니다.

---

# 6. Actual State로서 Configuration Snapshot

Configuration Snapshot은 특정 시점에 실제 DB에서 수집한 설정값입니다.

MySQL에서는 다음 SQL을 사용합니다.

```sql
SHOW GLOBAL VARIABLES;
```

이 결과는 다음과 같은 형태입니다.

```text
Variable_name      Value
slow_query_log     ON
long_query_time    10.000000
binlog_format      ROW
```

이 값을 그대로 Profile과 비교할 수도 있지만, 운영 플랫폼에서는 실제 수집값 자체도 이력으로 남기는 것이 좋습니다.

그래서 Snapshot과 SnapshotItem을 분리했습니다.

```text
ConfigurationSnapshot
  - databaseId
  - engineType
  - capturedAt

ConfigurationSnapshotItem
  - snapshotId
  - parameterName
  - actualValue
  - unit
  - valueType
  - dynamic
  - source
```

Snapshot은 "수집 시점의 묶음"입니다. SnapshotItem은 그 안의 개별 설정값입니다.

이 구조를 선택한 이유는 설정 항목이 DBMS마다 다르고, 항목 수가 계속 바뀔 수 있기 때문입니다.

나쁜 구조는 다음과 같습니다.

```text
configuration_snapshot
  - databaseId
  - max_connections
  - slow_query_log
  - long_query_time
  - binlog_format
```

이 구조는 MySQL에는 맞을 수 있지만 PostgreSQL을 추가하는 순간 문제가 생깁니다. PostgreSQL에는 다른 설정 항목이 있고, DBMS 버전마다 항목도 달라질 수 있습니다. 설정 항목이 늘어날 때마다 컬럼을 추가하는 방식은 확장성이 떨어집니다.

그래서 다음 구조를 선택했습니다.

```text
configuration_snapshot
  - id
  - databaseId
  - engineType
  - capturedAt

configuration_snapshot_item
  - snapshotId
  - parameterName
  - actualValue
```

이렇게 하면 MySQL, PostgreSQL, 다른 DBMS가 모두 같은 저장 구조를 사용할 수 있습니다.

---

# 7. DBMS별 설정 수집을 Adapter로 분리한 이유

Configuration Snapshot을 만들려면 실제 DB에 접속해서 설정값을 읽어야 합니다.

하지만 DBMS마다 설정값을 조회하는 방식이 다릅니다.

MySQL은 다음을 사용합니다.

```sql
SHOW GLOBAL VARIABLES;
```

PostgreSQL은 다음을 사용할 수 있습니다.

```sql
SELECT name, setting, unit, vartype, context, source
FROM pg_settings;
```

이 차이를 `ConfigurationSnapshotService` 안에 직접 넣으면 문제가 생깁니다.

나쁜 구조는 다음과 같습니다.

```text
ConfigurationSnapshotService
  - if MYSQL then SHOW GLOBAL VARIABLES
  - if POSTGRESQL then SELECT FROM pg_settings
  - if ORACLE then ...
```

이렇게 되면 policy 모듈이 DBMS별 SQL을 모두 알아야 합니다. DBMS가 늘어날수록 Service가 커지고, 테스트도 어려워집니다.

그래서 DBMS별 수집 책임을 Adapter로 분리했습니다.

현재 구조는 다음과 같습니다.

```text
policy.application.ConfigurationSnapshotService
      │
      ▼
database.application.DatabaseConfigurationReaderPortRegistry
      │
      ▼
database.application.DatabaseConfigurationReaderPort
      │
      ▼
database.infra.MySqlConfigurationReaderAdapter
```

`ConfigurationSnapshotService`는 “설정값을 수집해줘”라고 요청할 뿐입니다.

```java
reader.collectConfiguration(databaseId)
```

실제로 어떤 SQL을 실행할지는 Adapter가 결정합니다.

```text
MySqlConfigurationReaderAdapter
  - SHOW GLOBAL VARIABLES 실행
```

이 구조의 장점은 DBMS 확장 시 명확합니다.

```text
DatabaseConfigurationReaderPort
  ├── MySqlConfigurationReaderAdapter
  └── PostgreSqlConfigurationReaderAdapter
```

새로운 DBMS가 추가되어도 policy 쪽 비교/저장 로직은 그대로 유지할 수 있습니다.

---

# 8. policy와 database 모듈을 분리한 이유

이번에 고민했던 부분 중 하나는 Configuration 관련 코드를 어디에 둘 것인가였습니다.

프로젝트에는 이미 `database` 모듈이 있습니다.

```text
database
├── api
├── application
├── domain
├── dto
└── infra
```

그리고 `policy` 모듈이 추가되었습니다.

결론적으로 다음 기준으로 나눴습니다.

```text
database 모듈
  - 실제 DB 인스턴스 정보
  - Credential
  - DBMS별 접속 방식
  - DBMS별 SQL 실행

policy 모듈
  - 운영 기준
  - Snapshot 저장
  - Profile과 Snapshot 비교
  - Drift 판단
```

즉, `SHOW GLOBAL VARIABLES`는 database 모듈의 책임입니다. 반면 “slow_query_log가 ON이어야 한다”는 기준과 그 기준에 맞는지 판단하는 것은 policy 모듈의 책임입니다.

이렇게 나눈 이유는 실제 DB 접속 방식과 운영 정책 판단을 분리하기 위해서입니다.

만약 policy 모듈이 Credential과 JDBC URL 구성, MySQL SQL까지 모두 알게 되면 모듈 경계가 흐려집니다. 반대로 database 모듈이 Profile과 Drift 판단까지 알게 되면 database 모듈이 운영 정책까지 담당하게 됩니다.

그래서 다음 의존 방향을 유지했습니다.

```text
policy.application
  ↓
database.application port
  ↓
database.infra adapter
```

이 구조는 policy가 database의 구현체가 아니라 port에 의존하게 만들기 위한 선택입니다.

---

# 9. Profile과 Snapshot 비교 로직

Configuration Drift의 핵심은 ProfileParameter와 SnapshotItem을 비교하는 것입니다.

비교 흐름은 다음과 같습니다.

```text
profileId + snapshotId 입력
      │
      ▼
ConfigurationProfile 조회
      │
      ▼
ConfigurationSnapshot 조회
      │
      ▼
engineType 일치 여부 확인
      │
      ▼
ProfileParameter 목록 조회
      │
      ▼
SnapshotItem 목록 조회
      │
      ▼
SnapshotItem을 parameterName 기준 Map으로 변환
      │
      ▼
ProfileParameter 기준으로 하나씩 비교
      │
      ▼
ConfigurationComparisonResult 생성
```

비교 기준을 SnapshotItem이 아니라 ProfileParameter로 잡은 이유가 중요합니다.

Drift는 “우리가 기대한 기준이 실제 DB에서 지켜지고 있는가”를 판단하는 기능입니다. 따라서 비교의 기준은 실제 DB에 존재하는 모든 설정값이 아니라 Profile에 정의한 항목입니다.

예를 들어 Profile이 다음과 같다고 하겠습니다.

```text
slow_query_log
long_query_time
binlog_format
```

실제 Snapshot에는 다음 값이 있을 수 있습니다.

```text
slow_query_log
long_query_time
max_connections
```

이 경우 비교 결과는 다음이 되어야 합니다.

```text
slow_query_log   → 비교
long_query_time  → 비교
binlog_format    → MISSING
```

Snapshot에 있는 `max_connections`는 Profile에 없기 때문에 이번 비교 대상이 아닙니다.

이것은 의도적인 설계입니다.

```text
Profile에 정의한 기준만 검사한다.
```

나중에 Profile에 없지만 실제 DB에 존재하는 항목까지 보고 싶다면 `EXTRA` 상태를 추가할 수 있습니다. 하지만 운영 기준 준수 여부가 핵심이므로 `EXTRA`는 제외했습니다.

---

# 10. 비교 전 engineType을 검증한 이유

Profile과 Snapshot을 비교하기 전에 반드시 engineType을 검증합니다.

```text
profile.engineType == snapshot.engineType
```

이 검증이 필요한 이유는 MySQL Profile과 PostgreSQL Snapshot을 비교하면 안 되기 때문입니다.

예를 들어 MySQL Profile에는 다음 기준이 있습니다.

```text
slow_query_log
binlog_format
```

PostgreSQL Snapshot에는 다음 설정이 있습니다.

```text
log_min_duration_statement
shared_buffers
```

둘은 설정 체계 자체가 다릅니다. 이름이 우연히 같더라도 의미가 다를 수 있습니다. 따라서 engineType이 다르면 비교 자체를 잘못된 요청으로 보고 예외 처리합니다.

이 검증을 통해 잘못된 Drift 결과가 저장되는 것을 막을 수 있습니다.

---

# 11. 값 비교를 단순 문자열 비교로 처리하지 않은 이유

DB 설정값은 문자열로 조회되지만, 의미상 같은 값이 서로 다른 문자열로 표현될 수 있습니다.

예를 들어 숫자 설정은 다음과 같이 표현될 수 있습니다.

```text
expectedValue = 1.0
actualValue   = 1.000000
```

문자열로 보면 다르지만 숫자로는 같습니다.

Boolean 값도 마찬가지입니다.

```text
expectedValue = ON
actualValue   = 1
```

MySQL에서는 설정값이 `ON/OFF` 또는 `1/0` 형태로 표현될 수 있습니다. 이것을 단순 문자열 비교하면 실제로는 같은 의미인데 Drift로 잘못 판단할 수 있습니다.

그래서 `ParameterValueType`을 두고 타입별 비교 방식을 분리했습니다.

```text
STRING
NUMBER
BOOLEAN
```

비교 규칙은 다음과 같습니다.

## STRING

문자열은 앞뒤 공백을 제거하고 대소문자를 정규화한 뒤 비교합니다.

```text
expectedValue = ROW
actualValue   = row
결과           = COMPLIANT
```

## NUMBER

숫자는 `BigDecimal`로 변환한 뒤 숫자 의미 기준으로 비교합니다.

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

Boolean은 다음 값들을 같은 의미로 봅니다.

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

이 비교 로직은 `ConfigurationValueComparator`로 분리했습니다.

이렇게 분리한 이유는 `ConfigurationComparisonService`가 비교 흐름을 담당하고, 실제 값 비교 규칙은 별도 컴포넌트가 담당하도록 하기 위해서입니다.

```text
ConfigurationComparisonService
  - 어떤 항목을 비교할지 결정

ConfigurationValueComparator
  - 값을 어떻게 비교할지 결정
```

---

# 12. ComparisonResult와 Drift 저장을 분리한 이유

비교 결과를 바로 DB에 저장할 수도 있습니다.

예를 들어 `ConfigurationComparisonService.compare()` 안에서 바로 `ConfigurationDrift`를 저장하는 방식입니다.

하지만 그렇게 하지 않았습니다.

대신 다음처럼 분리했습니다.

```text
ConfigurationComparisonService
  ↓
ConfigurationComparisonResult 반환
  ↓
ConfigurationDriftService
  ↓
ConfigurationDrift 저장
```

이렇게 분리한 이유는 비교 로직과 저장 정책이 서로 다른 책임이기 때문입니다.

비교 로직은 순수하게 expected와 actual을 비교해서 결과를 만드는 일입니다. 반면 Drift 저장은 비교 결과를 이력으로 남기는 일입니다.

분리하면 다음 장점이 있습니다.

첫째, 비교 로직을 단위 테스트하기 쉽습니다. DB 저장을 신경 쓰지 않고 여러 값 조합을 테스트할 수 있습니다.

둘째, 저장 정책을 나중에 바꾸기 쉽습니다. 예를 들어 모든 비교 결과를 저장할지, NON_COMPLIANT만 저장할지, 동일한 결과가 반복되면 저장을 생략할지 같은 정책을 별도로 다룰 수 있습니다.

셋째, 같은 ComparisonResult를 API 응답, Alert 평가, Drift 저장 등 여러 곳에서 재사용할 수 있습니다.

그래서 Phase 6에서는 비교 결과 객체인 `ConfigurationComparisonResult`를 먼저 만들고, 그 결과를 `ConfigurationDriftService`가 저장하도록 설계했습니다.

---

# 13. Drift와 DriftItem을 분리한 이유

Drift는 하나의 점검 결과입니다.

DriftItem은 그 점검 결과 안의 개별 항목입니다.

```text
ConfigurationDrift
  - databaseId
  - profileId
  - snapshotId
  - engineType
  - status
  - totalCount
  - compliantCount
  - nonCompliantCount
  - missingCount
  - checkedAt

ConfigurationDriftItem
  - driftId
  - parameterName
  - expectedValue
  - actualValue
  - valueType
  - required
  - dynamic
  - applyAllowed
  - complianceStatus
  - message
```

이렇게 분리한 이유는 목록 조회와 상세 조회의 요구가 다르기 때문입니다.

DB 상세 화면에서는 최근 Drift의 요약만 보고 싶을 수 있습니다.

```text
status = NON_COMPLIANT
totalCount = 10
nonCompliantCount = 2
missingCount = 1
```

반면 상세 화면에서는 어떤 항목이 왜 다른지 봐야 합니다.

```text
long_query_time
expected = 1.0
actual = 10.000000
status = NON_COMPLIANT
```

Drift와 DriftItem을 분리하면 목록 조회에서는 Drift만 조회하고, 상세 조회에서는 DriftItem까지 조회할 수 있습니다.

이 방식은 응답 크기를 줄이고, 조회 목적에 맞는 API를 설계하는 데 유리합니다.

---

# 14. Drift 상태를 두 단계로 나눈 이유

이번 설계에서는 상태가 두 종류 있습니다.

```text
ConfigurationDriftStatus
ComplianceStatus
```

`ConfigurationDriftStatus`는 점검 전체의 상태입니다.

```text
COMPLIANT
NON_COMPLIANT
```

`ComplianceStatus`는 개별 항목의 상태입니다.

```text
COMPLIANT
NON_COMPLIANT
MISSING
```

전체 Drift 상태에는 `MISSING`을 두지 않았습니다. MISSING은 개별 항목에서만 의미가 있기 때문입니다.

예를 들어 다음 결과가 있다고 하겠습니다.

```text
slow_query_log   COMPLIANT
long_query_time  COMPLIANT
binlog_format    MISSING
```

전체 점검은 정상이라고 볼 수 없습니다. 따라서 Drift 전체 상태는 `NON_COMPLIANT`입니다.

즉, 전체 상태는 단순화했습니다.

```text
모든 항목이 COMPLIANT
  → ConfigurationDriftStatus.COMPLIANT

하나라도 NON_COMPLIANT 또는 MISSING
  → ConfigurationDriftStatus.NON_COMPLIANT
```

이렇게 하면 운영 화면에서 현재 DB의 기준 준수 여부를 빠르게 판단할 수 있습니다.

---

# 15. Configuration Check를 OperationJob으로 만든 이유

Configuration Check는 읽기 작업에 가까워 보입니다. 실제 설정값을 조회하고 비교하는 작업이기 때문입니다.

하지만 HTTP 요청 안에서 바로 실행하지 않고 OperationJob으로 만들었습니다.

이유는 다음과 같습니다.

첫째, DB 접속이 실패할 수 있습니다. 대상 DB가 일시적으로 내려가 있거나 네트워크 문제가 있을 수 있습니다.

둘째, 수집과 저장 과정이 여러 단계로 나뉩니다.

```text
DB 접속
SHOW GLOBAL VARIABLES 실행
Snapshot 저장
Profile 비교
Drift 저장
```

중간 단계에서 실패할 수 있으므로 작업 상태를 남겨야 합니다.

셋째, 같은 점검 요청이 중복 생성될 수 있습니다. 운영자가 버튼을 여러 번 누르거나 네트워크 재시도로 같은 요청이 들어올 수 있습니다. 이를 위해 기존 OperationJob의 Idempotency-Key 구조를 그대로 사용했습니다.

넷째, 나중에 스케줄 기반 점검이나 Alert 연동을 하기 쉽습니다. Configuration Check가 OperationJob으로 표현되어 있으면 수동 실행, 예약 실행, 조건 기반 실행을 같은 구조로 다룰 수 있습니다.

그래서 Configuration Check는 다음 흐름으로 실행됩니다.

```text
API 요청
      │
      ▼
OperationJob 생성
      │
      ▼
status = QUEUED
      │
      ▼
Worker Claim
      │
      ▼
status = RUNNING
      │
      ▼
ConfigurationCheckJobExecutor 실행
      │
      ▼
Drift 저장
      │
      ▼
status = SUCCEEDED
```

---

# 16. BACKUP과 CONFIGURATION_CHECK의 실행 방식을 다르게 한 이유

DB FleetOps에는 이미 Backup Job 구조가 있습니다.

Backup은 Agent 기반으로 실행합니다.

```text
BACKUP Job
  ↓
OperationTask 생성
  ↓
Agent가 mysqldump 실행
  ↓
Task 완료
  ↓
Job 완료
```

반면 Configuration Check는 OperationTask를 만들지 않고 Control Plane Worker가 직접 실행합니다.

```text
CONFIGURATION_CHECK Job
  ↓
Worker가 직접 JDBC 접속
  ↓
SHOW GLOBAL VARIABLES 실행
  ↓
Snapshot / Drift 저장
  ↓
Job 완료
```

이렇게 나눈 이유는 작업의 성격이 다르기 때문입니다.

Backup은 DB Host 근처에서 실행해야 할 가능성이 큽니다. `mysqldump`, 파일 저장, checksum 계산, 복원 검증 같은 작업은 Agent가 수행하는 것이 적절합니다.

반면 Configuration Check는 JDBC 접속만 가능하면 Control Plane에서도 수행할 수 있습니다. 파일 시스템 작업이나 OS 명령 실행이 필요하지 않습니다.

따라서 Configuration Check를 Agent Task로 만들지 않았습니다.

이 선택은 구조를 단순하게 유지하면서도 작업 성격에 맞는 실행 방식을 적용하기 위한 것입니다.

---

# 17. ConfigurationCheckJobExecutor를 분리한 이유

처음에는 `OperationWorkerService` 안에 Configuration Check 실행 로직을 모두 넣을 수 있습니다.

하지만 그렇게 하면 Worker가 너무 많은 책임을 갖게 됩니다.

나쁜 구조는 다음과 같습니다.

```text
OperationWorkerService
  - Job Claim
  - Lease 설정
  - Backup Task 생성
  - Configuration Check payload 파싱
  - Snapshot 생성
  - 비교 실행
  - Drift 저장
  - 성공/실패 처리
```

Worker의 본래 책임은 Job을 가져오고 상태를 전이시키는 것입니다.  
Configuration Check의 세부 실행 흐름은 별도 컴포넌트가 맡는 것이 좋습니다.

그래서 `ConfigurationCheckJobExecutor`를 만들었습니다.

```text
OperationWorkerService
  - Job claim
  - JobType 분기
  - 성공/실패 상태 처리

ConfigurationCheckJobExecutor
  - payload 파싱
  - ManagedDatabase 조회
  - engineType 변환
  - Snapshot 생성
  - 비교 실행
  - Drift 저장
```

이렇게 나누면 Worker는 실행 흐름을 제어하고, Executor는 특정 JobType의 실제 실행 로직을 담당합니다.

나중에 설정 변경 Job이 추가되더라도 같은 방식으로 확장할 수 있습니다.

```text
ConfigurationApplyJobExecutor
BackupJobExecutor
RestoreVerifyJobExecutor
```

---

# 18. Job Payload에 profileId를 저장한 이유

Configuration Check Job은 실행 시점에 어떤 Profile로 비교할지 알아야 합니다.

Job의 대상 DB는 `targetDatabaseId`로 저장됩니다. 하지만 Profile은 별도로 필요합니다.

그래서 Job payload에 `profileId`를 저장했습니다.

예시:

```json
{
  "profileId": 1,
  "reason": "daily configuration compliance check",
  "requestedBy": "local-user"
}
```

이 구조를 선택한 이유는 OperationJob의 공통 구조를 유지하기 위해서입니다.

OperationJob에 `profileId` 컬럼을 직접 추가하는 방법도 있지만, 그렇게 하면 JobType이 늘어날 때마다 컬럼이 계속 늘어날 수 있습니다.

예를 들어 앞으로 이런 값들이 필요할 수 있습니다.

```text
backupPath
restoreTarget
parameterName
expectedValue
applyMode
approvalId
```

이 값을 모두 OperationJob 컬럼으로 만들면 Job 테이블이 특정 작업들에 의해 오염됩니다.

그래서 JobType별 추가 입력값은 payloadJson에 저장하고, 공통적으로 필요한 값만 컬럼으로 유지했습니다.

```text
OperationJob 공통 컬럼
  - jobType
  - targetDatabaseId
  - status
  - requestedBy
  - idempotencyKey
  - requestPayloadJson

JobType별 상세 값
  - requestPayloadJson 내부에 저장
```

이 방식은 유연하지만 단점도 있습니다. payloadJson 내부 필드는 DB 차원에서 타입 검증이 어렵습니다. 그래서 Executor에서 payload를 파싱한 뒤 필수값을 명확하게 검증합니다.

```text
profileId가 없으면 실행 실패 처리
payloadJson이 깨져 있으면 실행 실패 처리
```

---

# 19. Idempotency-Key를 적용한 이유

Configuration Check Job 생성에도 Idempotency-Key를 적용했습니다.

이유는 사용자가 같은 요청을 여러 번 보낼 수 있기 때문입니다.

예를 들어 운영자가 설정 점검 버튼을 눌렀는데 응답이 늦어 다시 눌렀다고 하겠습니다. 또는 네트워크 재시도로 같은 요청이 두 번 들어올 수 있습니다.

Idempotency-Key가 없다면 같은 Configuration Check Job이 여러 개 생성될 수 있습니다.

```text
CONFIGURATION_CHECK Job #1
CONFIGURATION_CHECK Job #2
CONFIGURATION_CHECK Job #3
```

이 작업은 읽기 중심이라 백업보다 위험도는 낮지만, 불필요한 DB 접속과 중복 Drift 이력이 생깁니다. 운영자가 결과를 볼 때도 어떤 점검 결과가 의미 있는지 혼란이 생길 수 있습니다.

그래서 기존 Backup Job과 같은 방식으로 처리했습니다.

```text
databaseId + jobType + idempotencyKey
```

같은 키로 이미 생성된 Job이 있으면 새로 만들지 않고 기존 Job을 반환합니다.

이 구조는 API 재시도에 안전합니다.

---

# 20. 실패와 재시도 처리

Configuration Check는 다음 지점에서 실패할 수 있습니다.

```text
payloadJson 파싱 실패
profileId 누락
ManagedDatabase 없음
engineType 없음
DB 접속 실패
SHOW GLOBAL VARIABLES 실패
Profile 없음
Profile과 Snapshot의 engineType 불일치
Drift 저장 실패
```

Worker는 예외가 발생하면 Job을 실패 처리합니다.

```text
RUNNING
  ↓
FAILED
```

그리고 retry 가능한 경우 다시 대기 상태로 전환합니다.

```text
FAILED
  ↓
QUEUED
```

현재 구조에서는 retryable 여부를 외부 fail API에서도 받고, Worker 내부 실행 실패의 경우 retry 가능한 것으로 처리했습니다. DB 접속 실패나 일시적인 네트워크 오류는 재시도로 해결될 수 있기 때문입니다.

다만 모든 오류가 재시도 대상은 아닙니다.

예를 들어 payloadJson이 깨져 있거나 profileId가 없는 경우는 재시도해도 해결되지 않습니다. 현재는 단순화를 위해 Worker 내부 실패를 retryable로 처리했지만, 후속 개선에서는 예외 유형에 따라 retry 가능 여부를 나누는 것이 좋습니다.

예상 개선 방향은 다음과 같습니다.

```text
Invalid payload
Profile not found
Engine type mismatch
  → retry 불필요

DB connection timeout
Transient network error
Lock wait timeout
  → retry 가능
```

이 개선은 이후 Job Error Classification에서 다룰 수 있습니다.

---

# 21. 조회 API를 목록과 상세로 분리한 이유

Drift 조회 API는 세 가지를 제공합니다.

```text
GET /api/v1/database-instances/{databaseId}/configuration-drifts/latest
GET /api/v1/database-instances/{databaseId}/configuration-drifts
GET /api/v1/configuration-drifts/{driftId}
```

각 API의 목적은 다릅니다.

`latest` API는 DB의 현재 기준 준수 상태를 빠르게 보기 위한 것입니다.

```text
이 DB는 현재 표준 설정과 맞는가?
```

목록 API는 최근 Drift 이력을 보기 위한 것입니다.

```text
최근 점검 결과가 어떻게 변했는가?
언제부터 NON_COMPLIANT가 되었는가?
```

상세 API는 특정 Drift의 개별 항목을 보기 위한 것입니다.

```text
어떤 설정이 왜 다른가?
expected와 actual은 무엇인가?
applyAllowed 대상인가?
```

목록 API에서는 DriftItem을 포함하지 않았습니다. 이유는 응답 크기 때문입니다.

예를 들어 Drift 이력 10개를 조회하고, 각 Drift마다 100개 설정 항목이 있으면 한 번의 목록 조회에서 1,000개 DriftItem이 내려갑니다. 이는 목록 화면에는 불필요하게 무겁습니다.

그래서 목록은 요약만 반환하고, 상세 조회에서만 Item을 포함하도록 했습니다.

---

# 22. 구조적 효과

이번 작업을 통해 DB FleetOps는 단순 Health Check 도구에서 한 단계 확장되었습니다.

기존에는 다음 질문에 답했습니다.

```text
DB에 접속 가능한가?
```

이제는 다음 질문에 답할 수 있습니다.

```text
DB가 운영 기준에 맞게 설정되어 있는가?
어떤 설정이 다른가?
그 차이가 언제 발생했는가?
반복적으로 Drift가 발생하는가?
```

구조적으로는 다음 기반이 생겼습니다.

```text
Desired State 관리
Actual State 수집
Expected vs Actual 비교
Drift 이력 저장
Job 기반 점검 실행
API 기반 조회
```

이 구조는 후속 작업의 기반이 됩니다.

---

# 23. 후속 작업과의 연결

Configuration Drift는 이후 기능의 출발점입니다.

## Safe Configuration Apply

Drift 결과를 보면 어떤 값이 기준과 다른지 알 수 있습니다.  
다음 단계에서는 이 차이를 기반으로 안전하게 설정 변경을 수행할 수 있습니다.

예상 흐름은 다음과 같습니다.

```text
DriftItem NON_COMPLIANT
      │
      ▼
dynamic / applyAllowed 확인
      │
      ▼
Configuration Apply Job 생성
      │
      ▼
변경 전 Snapshot 저장
      │
      ▼
SET GLOBAL 실행
      │
      ▼
변경 후 Snapshot 저장
      │
      ▼
재비교
      │
      ▼
성공 여부 판단
```

Phase 6에서 `dynamic`, `applyAllowed`를 DriftItem에 저장한 것도 이 흐름을 염두에 둔 선택입니다.

## Alert 연동

Configuration Drift가 NON_COMPLIANT이면 Alert로 연결할 수 있습니다.

```text
ConfigurationDrift.status = NON_COMPLIANT
      │
      ▼
AlertEvent 생성
      │
      ▼
Runbook 연결
```

이렇게 하면 운영자는 단순히 "설정이 다르다"는 정보만 보는 것이 아니라, 어떤 조치를 해야 하는지까지 이어서 볼 수 있습니다.

## PostgreSQL 확장

현재 상황은 MySQL만 지원합니다. 하지만 수집 로직은 Adapter로 분리했기 때문에 PostgreSQL을 추가할 수 있습니다.

```text
DatabaseConfigurationReaderPort
  ├── MySqlConfigurationReaderAdapter
  └── PostgreSqlConfigurationReaderAdapter
```

PostgreSQL Adapter는 `pg_settings`를 조회하면 됩니다.

```sql
SELECT
    name,
    setting,
    unit,
    vartype,
    context,
    source
FROM pg_settings;
```

Profile, Snapshot, Comparison, Drift 저장 구조는 그대로 재사용할 수 있습니다.

---

# 24. 현재 설계의 한계

의도적으로 단순화한 부분도 있습니다.

첫째, Profile Parameter가 하나도 없는 경우를 아직 강하게 막지 않았습니다. 현재 구조에서는 Parameter가 0개면 비교 결과도 0개이고 전체 상태가 COMPLIANT가 될 수 있습니다. 실제 운영에서는 의미 없는 Profile이므로 활성화 시점에 Parameter 최소 1개 이상을 검증하는 것이 좋습니다.

둘째, payloadJson은 유연하지만 DB 수준 타입 안정성이 약합니다. profileId 같은 중요한 값은 JSON 안에 있으므로 실행 시점에 검증해야 합니다. JobType별 payload schema 검증을 강화하면 더 안전해집니다.

셋째, Configuration Check 실패 시 모든 예외를 같은 방식으로 retryable 처리할 수 있습니다. 향후에는 예외 유형별로 retry 정책을 나누는 것이 좋습니다.

넷째, SnapshotItem의 valueType과 dynamic 정보는 MySQL `SHOW GLOBAL VARIABLES`만으로는 충분히 알기 어렵습니다. 현재는 ProfileParameter의 valueType을 기준으로 비교하고 있습니다. PostgreSQL의 `pg_settings`처럼 설정 메타데이터를 더 많이 제공하는 DBMS에서는 이 값을 더 풍부하게 채울 수 있습니다.

다섯째, Drift 조회는 최근 10개 기준으로 단순화했습니다. 실제 운영에서는 기간 조건, status 필터, profileId 필터, pagination이 필요할 수 있습니다.

이 한계들은 현재 구조를 깨지 않고 확장할 수 있습니다. 흐름을 먼저 안정적으로 만드는 것이 우선이었습니다.

---

# 25. 정리

DB 설정값을 단순 조회하는 기능이 아니라, 운영 기준과 실제 상태의 차이를 관리하는 구조를 만들었습니다.

핵심 설계는 다음과 같습니다.

```text
Profile
  - 기대하는 운영 기준

Snapshot
  - 실제 DB에서 수집한 설정값

Comparison
  - 기대값과 실제값의 비교 결과

Drift
  - 비교 결과를 저장한 이력

OperationJob
  - 설정 점검 실행 단위
```

이 구조를 통해 DB FleetOps는 다음 능력을 갖게 되었습니다.

```text
DB별 표준 설정 기준 정의
실제 DB 설정값 수집
타입별 비교
불일치 항목 탐지
점검 결과 이력화
Job 기반 비동기 실행
API 기반 조회
```

가장 중요한 포인트는 "DB 설정을 지금 조회할 수 있다"가 아닙니다.

중요한 것은 다음입니다.

```text
운영 기준을 Desired State로 정의하고,
실제 DB 상태를 Snapshot으로 남기며,
두 상태의 차이를 Drift로 이력화할 수 있는 구조를 만들었다.
```

이 구조는 이후 설정 자동 변경, Alert, Runbook, PostgreSQL 확장, 운영 대시보드로 이어지는 기반이 됩니다.