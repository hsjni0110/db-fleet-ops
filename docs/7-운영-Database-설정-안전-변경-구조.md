# 운영 Database 설정 안전 변경 구조

## 1. 이 문서의 범위

이 문서는 운영 기준과 다른 설정을 검증한 뒤 Database에 적용하고, 변경 결과를 다시 확인하는 구조만 설명합니다.

- Job 접수와 재시도는 [3번 문서](3-비동기-작업-처리를-위한-Job-구조.md)를 참고합니다.
- Profile, Snapshot과 Drift는 [6번 문서](6-Database-설정-차이-점검-구조.md)를 참고합니다.

## 2. 설정 변경을 바로 실행하지 않는 이유

설정 조회와 달리 설정 변경은 Database 동작에 직접 영향을 줍니다. 따라서 요청값을 바로 `SET GLOBAL`로 실행하지 않고 다음 조건을 먼저 확인합니다.

```text
Profile에 등록된 설정인가
  → 실행 중 변경할 수 있는가
  → 플랫폼에서 변경을 허용했는가
  → 값의 형식이 올바른가
  → 같은 Database에서 다른 변경이 실행 중이지 않은가
```

하나라도 만족하지 않으면 Job을 만들기 전에 요청을 거절합니다.

## 3. 전체 실행 흐름

```text
설정 변경 요청 검증
  → CONFIGURATION_APPLY Job 생성
  → Worker가 Job 가져오기
  → 변경 전 Snapshot 저장
  → 설정 항목별 변경 실행
  → 변경 후 Snapshot 저장
  → 요청값과 실제값 재비교
  → Apply와 Job 결과 저장
```

명령 실행이 성공했다는 사실만으로는 설정 변경이 성공했다고 판단하지 않습니다. 변경 후 Database에서 값을 다시 읽어 요청값과 같아야 최종 성공입니다.

## 4. Job과 Apply 장부를 나눈 이유

`OperationJob`은 비동기 실행 상태와 재시도를 관리합니다. `ConfigurationApply`는 어떤 설정을 어떤 이유로 변경했고 실제 결과가 무엇인지 기록합니다.

```text
OperationJob
  → QUEUED, RUNNING, SUCCEEDED, FAILED

ConfigurationApply
  → 요청자와 변경 사유
  → 변경 전후 Snapshot
  → 성공·실패·건너뜀 개수
  → 항목별 변경 결과
```

두 장부를 분리하면 Job Engine의 실행 정보와 설정 변경 감사 정보를 섞지 않고 조회할 수 있습니다.

## 5. 변경 요청 검증

`ConfigurationApplyValidationService`는 다음 규칙을 검사합니다.

- 요청한 Profile과 Parameter가 존재해야 합니다.
- 같은 Parameter를 한 요청에 두 번 넣을 수 없습니다.
- `dynamic=true`인 설정만 지원합니다.
- `applyAllowed=true`인 설정만 변경할 수 있습니다.
- 같은 Database에 `REQUESTED` 또는 `RUNNING` Apply가 있으면 새 요청을 거절합니다.
- BOOLEAN과 NUMBER는 각 값 종류에 맞게 해석할 수 있어야 합니다.
- STRING에 `;`, `--`, `/*`, `*/` 같은 위험한 SQL 조각이 있으면 거절합니다.

이 검증은 Job 생성 전과 실제 실행 직전에 다시 수행합니다. 대기 중 Profile이나 실행 상태가 바뀌었을 수 있기 때문입니다.

## 6. MySQL 변경 범위 제한

`MySqlConfigurationApplyAdapter`는 다음 세 Parameter만 허용합니다.

```text
slow_query_log
long_query_time
max_connections
```

Parameter 이름은 허용 목록에서만 선택하고, 값은 BOOLEAN·NUMBER·STRING 규칙으로 정규화합니다. 문자열의 작은따옴표도 SQL literal 규칙에 맞게 처리합니다.

현재 변경 방식은 `SET GLOBAL`입니다. 설정 파일 수정과 Database 재시작이 필요한 정적 설정은 지원하지 않습니다.

## 7. 변경 전후 확인

`ConfigurationChangeExecution`은 변경 전 Snapshot을 저장한 후 각 설정을 변경합니다. 이후 다시 Snapshot을 만들고 `ConfigurationValueComparator`로 요청값과 실제값을 비교합니다.

항목 상태의 핵심 의미는 다음과 같습니다.

```text
PENDING      아직 실행하지 않음
APPLIED      변경 명령은 성공함
VERIFIED     변경 후 실제값도 요청값과 같음
FAILED       명령 또는 사후 검증 실패
SKIPPED      실행하지 않음
UNSUPPORTED  현재 Adapter에서 지원하지 않음
```

모든 항목이 검증되면 Apply는 `SUCCEEDED`가 됩니다. 일부 항목만 성공하면 `PARTIALLY_SUCCEEDED`, 모두 실패하면 `FAILED`가 됩니다.

## 8. Operation과 Policy의 역할

```text
Operation
  → 설정 변경 요청 (ConfigurationChange)

Policy
  → 요청 검증
  → 변경 전 Snapshot 수집
  → Database 설정 변경
  → 변경 후 Snapshot 수집과 결과 확인
  → Apply 장부 저장

Operation
  → 반환된 집계로 Job 성공 또는 실패 결정
```

`ConfigurationChangeAdapter`가 두 영역을 연결합니다. Operation은 `ConfigurationApply` Entity나 Policy 저장소를 직접 사용하지 않습니다.

## 9. 주요 코드 책임

| 코드 | 책임 |
|---|---|
| `ConfigurationApplyValidationService` | 변경 가능 여부와 요청값 검증 |
| `ConfigurationChangeExecution` | 변경 전 수집, 실행, 변경 후 확인 순서 조정 |
| `MySqlConfigurationApplyAdapter` | 허용된 MySQL 설정에 `SET GLOBAL` 실행 |
| `ConfigurationValueComparator` | 요청값과 변경 후 실제값 비교 |
| `ConfigurationApply` | 한 번의 설정 변경 결과와 집계 관리 |
| `ConfigurationApplyItem` | 설정 하나의 요청값, 전후값과 상태 관리 |
| `ConfigurationApplyJobExecution` | Apply 결과를 Job 결과로 변환 |

## 10. 자동 테스트로 확인한 내용

- 등록되지 않은 Parameter, 정적 설정과 변경 금지 설정을 거절합니다.
- 중복 Parameter와 잘못된 BOOLEAN·NUMBER·STRING 값을 거절합니다.
- 같은 Database의 동시 Apply 요청을 차단합니다.
- 변경 전후 값과 항목별 상태가 저장됩니다.
- 일부 실패는 `PARTIALLY_SUCCEEDED`, 전체 실패는 `FAILED`로 저장됩니다.
- Apply를 Job ID로 조회할 수 있습니다.

## 11. 아직 보완하거나 실험할 부분

- 승인 절차, 역할별 권한과 다중 승인 기능은 없습니다.
- 여러 Parameter를 하나의 DB Transaction처럼 원상 복구하는 기능은 없습니다.
- 애플리케이션 재시작 후에도 유지되는 설정 파일 변경은 지원하지 않습니다.
- 실제 운영 트래픽에서 `SET GLOBAL`이 성능과 연결에 미치는 영향은 실험하지 않았습니다.
- 현재 허용 목록은 세 항목뿐이며 DBMS 버전별 지원 차이를 자동 판정하지 않습니다.
- SQL 문자열을 직접 조합하므로 허용 목록과 값 검증이 안전성의 핵심입니다. 새로운 Parameter를 추가할 때 별도 보안 검토가 필요합니다.
