# Database 설정 차이 점검 구조

## 1. 이 문서의 범위

이 문서는 Database의 현재 설정이 운영 기준과 다른지 확인하고 그 결과를 저장하는 구조만 설명합니다.

- Job의 비동기 접수와 재시도는 [3번 문서](3-비동기-작업-처리를-위한-Job-구조.md)를 참고합니다.
- 중앙 관제 서버와 Worker의 역할은 [4번 문서](4-중앙-관제-서버와-Go-Agent-분리-구조.md)를 참고합니다.
- 차이가 난 설정을 실제로 변경하는 과정은 [7번 문서](7-운영-Database-설정-안전-변경-구조.md)를 참고합니다.

## 2. 설정 점검에서 사용하는 세 가지 정보

설정 점검은 기준, 현재값, 비교 결과를 서로 다른 장부로 관리합니다.

```text
ConfigurationProfile  운영자가 정한 설정 기준
ConfigurationSnapshot 점검 시점에 Database에서 읽은 현재 설정
ConfigurationDrift    기준과 현재 설정을 비교한 결과
```

이 정보를 분리하면 기준이 바뀌어도 과거에 어떤 현재값을 읽었고 왜 차이로 판정했는지 확인할 수 있습니다.

## 3. 전체 점검 흐름

설정 점검은 `CONFIGURATION_CHECK` Job으로 접수합니다.

```text
설정 점검 Job 생성
  → Worker가 Job 가져오기
  → Database의 현재 설정 수집
  → ConfigurationSnapshot 저장
  → ConfigurationProfile과 비교
  → ConfigurationDrift와 세부 항목 저장
  → Job 성공 또는 실패
```

점검은 중앙 Worker가 짧게 실행합니다. 백업처럼 DB Host에서 오랫동안 명령을 실행하는 작업이 아니므로 Agent Task로 만들지 않습니다.

## 4. 설정 기준

`ConfigurationProfile`은 특정 DBMS와 환경에 적용할 설정 기준입니다. 처음에는 `DRAFT`로 만들고 검토가 끝나면 `ACTIVE`로 바꿀 수 있습니다.

각 설정값은 `ConfigurationProfileParameter`로 따로 저장합니다.

```text
parameterName  설정 이름
expectedValue  기대값
valueType      STRING, NUMBER, BOOLEAN
required       반드시 존재해야 하는지
dynamic        실행 중 변경할 수 있는지
applyAllowed   자동 변경을 허용하는지
```

Profile과 Parameter를 나눈 이유는 하나의 기준에 여러 설정값이 들어가고, 각 설정값마다 비교 방법과 변경 가능 여부가 다르기 때문입니다.

## 5. 현재 설정 수집

`ConfigurationSnapshotService`는 DBMS 종류에 맞는 `DatabaseConfigurationReaderPort`를 선택해 현재 설정을 읽습니다. MySQL에서는 `MySqlConfigurationReaderAdapter`가 이 역할을 담당합니다.

```text
Database ID와 DBMS 종류 확인
  → DBMS에 맞는 Reader 선택
  → 현재 설정 목록 조회
  → ConfigurationSnapshot 저장
  → ConfigurationSnapshotItem 저장
```

Snapshot은 점검 당시의 값입니다. 이후 Database 설정이 바뀌어도 이미 저장된 Snapshot은 과거 점검 근거로 남습니다.

## 6. 설정값 비교

`ConfigurationComparisonService`는 Profile Parameter와 Snapshot Item을 설정 이름으로 연결합니다. 비교하기 전에 Profile과 Snapshot의 DBMS 종류가 같은지도 확인합니다.

비교 결과는 다음 세 가지입니다.

```text
COMPLIANT      기대값과 현재값이 같음
NON_COMPLIANT  설정은 있지만 값이 다름
MISSING        Profile에 정의된 설정이 현재값에 없음
```

문자열만 그대로 비교하면 같은 의미의 값도 다르다고 판단할 수 있으므로 값 종류에 따라 비교합니다.

```text
STRING   앞뒤 공백과 영문 대소문자를 정리한 뒤 비교
NUMBER   1, 1.0, 1.000을 같은 숫자로 비교
BOOLEAN  ON, TRUE, 1, YES를 true로 비교
         OFF, FALSE, 0, NO를 false로 비교
```

해석할 수 없는 숫자나 Boolean 값은 같다고 추측하지 않고 불일치로 처리합니다.

## 7. 차이 결과 저장과 조회

`ConfigurationDrift`에는 한 번의 점검 결과와 전체 집계를 저장합니다. 각 설정의 기대값, 현재값과 판정은 `ConfigurationDriftItem`에 저장합니다.

```text
모든 항목 일치              → Drift COMPLIANT
하나라도 불일치하거나 누락됨 → Drift NON_COMPLIANT
```

조회 API는 용도에 따라 나뉩니다.

- Database의 최근 결과: 현재 상태를 빠르게 확인합니다.
- Database의 최근 결과 목록: 점검 결과가 어떻게 변했는지 확인합니다.
- Drift 상세: 설정별 기대값과 현재값을 확인합니다.

목록은 최근 10건의 요약만 반환하고, 상세 조회에서 항목 전체를 반환합니다.

## 8. Operation과 Policy의 역할

Operation은 Job의 실행 상태를 관리하고, Policy는 설정을 수집하고 비교해 결과를 저장합니다.

```text
Operation
  → 설정 점검 요청 (ConfigurationCheck)

Policy
  → 현재 설정 수집
  → Profile과 Snapshot 비교
  → Drift 저장
  → 점검 결과 반환

Operation
  → 반환된 결과로 Job 상태 변경
```

Operation은 Profile, Snapshot, Drift Entity를 직접 사용하지 않습니다. `ConfigurationCheckAdapter`가 Operation의 요청을 Policy의 `ConfigurationCheckExecution`으로 연결합니다.

Job Payload에는 점검에 사용할 `profileId`를 저장합니다. Worker가 나중에 Job을 실행해도 요청 당시 선택한 기준을 알 수 있기 때문입니다.

## 9. 주요 코드 책임

| 코드 | 책임 |
|---|---|
| `ConfigurationProfileService` | Profile과 Parameter 생성·조회·상태 변경 |
| `ConfigurationSnapshotService` | DBMS별 현재 설정 수집과 Snapshot 저장 |
| `ConfigurationValueComparator` | 값 종류에 맞는 설정값 비교 |
| `ConfigurationComparisonService` | Profile과 Snapshot의 항목별 판정과 집계 |
| `ConfigurationDriftService` | Drift와 세부 항목 저장·조회 |
| `ConfigurationCheckExecution` | 수집, 비교, 저장 순서 조정 |
| `ConfigurationCheckJobExecution` | 점검 결과를 Job 성공·실패로 변환 |
| `ConfigurationCheckAdapter` | Operation의 점검 요청을 Policy에 연결 |

## 10. 자동 테스트로 확인한 내용

- Profile이 `DRAFT`로 생성되고 이름 중복이 거절됩니다.
- Profile에 Parameter를 추가하고 같은 Parameter의 중복을 거절합니다.
- Profile 활성화와 DBMS 종류별 목록 조회가 동작합니다.
- 현재 설정을 읽어 Snapshot과 Snapshot Item으로 저장합니다.
- 문자열, 숫자와 Boolean을 값 종류에 맞게 비교합니다.
- Profile과 Snapshot의 DBMS 종류가 다르면 비교를 거절합니다.
- 누락과 불일치 항목을 집계해 Drift와 Drift Item으로 저장합니다.
- 최근 Drift, 최근 10건의 목록과 세부 항목을 조회합니다.
- 설정 점검 결과가 Operation Job 상태에 반영됩니다.

## 11. 아직 보완하거나 실험할 부분

- 현재 자동 테스트는 비교 규칙과 저장 흐름을 검증하지만, 다양한 MySQL 버전의 전체 설정을 실제로 수집하는 호환성 실험은 부족합니다.
- `versionRange`는 Profile에 저장되지만 대상 Database 버전이 범위에 맞는지 자동으로 판정하지 않습니다.
- `required` 값은 결과에 함께 저장되지만 현재는 선택 항목도 누락되면 전체 Drift를 `NON_COMPLIANT`로 판정합니다.
- Profile 승인 이력, 변경 이력과 운영자별 권한은 아직 없습니다.
- 정기 점검 Scheduler, Drift Alert와 장기간 변화 추적은 후속 범위입니다.
- PostgreSQL Reader와 DBMS별 이름·단위 변환 규칙은 아직 구현하지 않았습니다.
