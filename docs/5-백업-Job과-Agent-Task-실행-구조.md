# 백업 Job과 Agent Task 실행 구조

## 1. 이 문서의 범위

이 문서는 백업 Job이 논리 백업과 복원 검증 Task로 실행되고, Task 결과로 Job의 최종 상태를 판정하는 구조만 설명합니다.

- Job의 비동기 접수와 Idempotency는 [3번 문서](3-비동기-작업-처리를-위한-Job-구조.md)를 참고합니다.
- Agent 분리, Poll, Task Lease와 결과 재전송은 [4번 문서](4-중앙-관제-서버와-Go-Agent-분리-구조.md)를 참고합니다.

## 2. 백업 Job 실행 흐름

`OperationJob`은 사용자의 백업 요청이고, `OperationTask`는 Agent가 실제로 실행하는 하나의 단계입니다.

```text
BACKUP Job
  → MYSQL_LOGICAL_BACKUP Task
  → 선택: MYSQL_RESTORE_VERIFY Task
  → Job 최종 성공 또는 실패
```

현재 백업은 순차적인 필수 단계입니다.

```text
중간 필수 Task 성공  → Job RUNNING
모든 필수 Task 성공 → Job SUCCEEDED
필수 Task 최종 실패    → Job FAILED
Task 재시도·Lease 회수 중 → Job RUNNING
```

## 3. 논리 백업 Task

`MYSQL_LOGICAL_BACKUP`은 Go Agent가 `mysqldump`를 실행하고 백업 파일의 존재, 크기, dump 형식과 SHA-256을 확인하는 Task입니다. 파일이 정상이고 `verifyAfterBackup=false`면 Job이 바로 성공합니다.

`verifyAfterBackup=true`면 백업 Task만 성공해서는 Job이 종료되지 않습니다. 같은 Agent에 `MYSQL_RESTORE_VERIFY` Task를 하나 생성하고 Job은 `RUNNING`을 유지합니다.

## 4. 복원 검증 Task

`MYSQL_RESTORE_VERIFY`는 임시 Database를 만들고 백업 파일을 실제로 복원한 뒤 Table과 선택적 Row Count를 확인합니다.

```text
복원 검증 성공 → Job SUCCEEDED
백업 또는 복원 검증 최종 실패 → Job FAILED
```

검증 결과는 `BackupRestoreVerificationResultRecorder`가 별도 장부에 저장하고, 정리 설정이 켜져 있으면 임시 Database를 삭제합니다.

## 5. Database와 Agent 연결

`ManagedDatabase.assignedAgentId`가 해당 Database에 접근할 대표 Agent를 명시합니다. 예전처럼 가장 최근에 응답한 Agent를 임의로 고르지 않습니다.

- Agent가 미지정이거나 존재하지 않으면 백업을 거절합니다.
- Agent가 `OFFLINE` 또는 `DISABLED`여도 거절합니다.
- 복원 검증 Task는 최초 백업 Task와 같은 Agent에 남습니다.

## 6. Credential을 Task에 넣지 않는 방법

Credential 비밀번호는 AES-256-GCM으로 암호화해 Metadata DB에 `v1:<nonce>:<ciphertext>` 형식으로 저장합니다. Task에는 사용자명과 비밀번호 대신 `credentialId`만 넣습니다.

```text
Agent가 Task 선점
  → 실행 직전 Credential API 호출
  → Control Plane이 Agent·Task·attempt·Lease·Database 배정 검증
  → 메모리에서만 Credential 사용
```

Agent는 비밀번호를 명령행 인자로 넘기지 않고 권한 `0600`의 임시 defaults 파일을 사용한 후 삭제합니다. 이전 attempt, 만료·종료 Task, 다른 Agent의 조회는 HTTP 409로 거절합니다.

## 7. Task 결과와 Job Lease 판정

Job Lease가 만료되어도 바로 백업 Task를 다시 만들지 않습니다. `ExpiredOperationJobService`가 연결 Task를 먼저 확인합니다.

```text
QUEUED/RUNNING Task 존재 → Job RUNNING + Job Lease만 연장
Task 없음 + 재시도 남음 → 같은 Job을 QUEUED로 회수
Task 없음 + 한도 도달 → Job TIMED_OUT
FAILED/TIMED_OUT Task 존재 → 상위 Job에 반영
모든 Task 종료 + Workflow 모순 → JOB_WORKFLOW_INCONSISTENT
```

Scheduler는 주기적 호출만 담당하고, 판정과 Transaction은 Service, 상태 변경 규칙은 `OperationJob`에 두었습니다.

## 8. 주요 코드 책임

| 코드 | 책임 |
|---|---|
| `BackupJobExecution` | BACKUP Job의 첫 Task 생성 지시 |
| `TaskReportService` | Task 결과를 한 번만 접수하고 후속 처리로 전달 |
| `BackupWorkflow` | 백업 결과로 Job을 판정하고 복원 검증 Task 생성 |
| `TaskCredentialService` | 실행 권한 검증 후 Credential 제공 |
| `CredentialCipher` | Credential AES-256-GCM 암·복호화 |
| `ExpiredJobService` | 만료 Job과 연결 Task 상태 조정 |
| `BackupPayloadBuilder` | 백업 결과를 복원 검증 입력으로 변환 |
| Go Backup/Restore Handler | Credential 조회, 백업·복원 실행과 정리 |

## 9. 자동 테스트로 확인한 내용

- Database에 배정된 ONLINE Agent로만 백업 Task가 생성됩니다.
- Task Payload에 username·password가 없고 `credentialId`만 남습니다.
- 같은 비밀번호도 매번 다른 nonce로 암호화되며 변조된 값은 복호화되지 않습니다.
- 백업 성공 후 복원 검증 Task가 하나만 생성되고, 최종 Task 결과가 Job에 반영됩니다.
- 활성 Task가 있는 만료 Job은 `RUNNING`을 유지하고 Task를 추가로 생성하지 않습니다.
- Task가 없는 만료 Job은 재대기되고 재시도 한도에서 `TIMED_OUT`됩니다.

## 10. 운영 적용 시 주의할 점

- `ManagedDatabase.assigned_agent_id`, `OperationTask.credential_id`와 인덱스 DDL을 애플리케이션보다 먼저 적용해야 합니다.
- `DB_FLEETOPS_CREDENTIAL_ENCRYPTION_KEY`는 Base64로 인코딩한 32-byte Key여야 하며 애플리케이션 외부 Secret으로 관리합니다.
- 기존 평문 Credential은 새 API로 다시 저장하고, 비밀번호가 든 기존 `QUEUED` Task는 배포 전에 정리해야 합니다.
- Agent–Control Plane 통신은 운영에서 TLS를 사용해야 합니다.

## 11. 아직 실험하지 않은 부분

- 11.1 수십·수백 GB 규모의 실제 백업·복원 성능은 장비 의존성 때문에 이번 범위에서 보류했습니다.
- Agent 자동 Failover, Credential의 Agent 디스크 보존, Secret Outbox는 아직 지원하지 않습니다.
- Object Storage 업로드, 보관 기간, 원격 Host 복구와 재해 복구 훈련은 후속 범위입니다.
