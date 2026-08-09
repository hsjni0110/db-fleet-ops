# DB FleetOps 배포와 관측성

## 1. 이 문서의 범위

이 문서는 DB FleetOps를 Docker Compose와 Kubernetes 예제로 실행하고, 상태와 주요 운영 지표를 확인하는 방법을 설명합니다.

중앙 관제 서버, Worker와 Agent를 나눈 이유는 [4번 문서](4-중앙-관제-서버와-Go-Agent-분리-구조.md)를 참고합니다. 이 문서에서는 그 구조를 어떻게 실행하고 관찰하는지만 다룹니다.

## 2. 실행 구성

Docker Compose는 다음 구성요소를 실행합니다.

```text
Metadata MySQL   Job, Task와 운영 장부 저장
Target MySQL     로컬 기능 확인 대상
API              HTTP 요청과 Agent API 처리
Worker           대기 Job 확인과 실행
Go Agent         Target MySQL 현장 작업 실행
Prometheus       API와 Worker 지표 수집
Grafana          수집한 지표 표시
```

API와 Worker는 같은 Spring Boot Image를 사용하지만 활성 Profile이 다릅니다.

```text
docker,api     Worker 스케줄러 비활성화
docker,worker  Worker 스케줄러 활성화
```

API를 여러 개 실행해도 각 API가 Job을 직접 가져가지 않고, Worker 수를 별도로 조절할 수 있습니다.

## 3. Container Image

루트 `Dockerfile`은 Java 21 JRE Image에 Spring Boot JAR와 상태 확인용 `curl`을 넣습니다. API와 Worker가 같은 Image를 사용하므로 빌드 결과가 달라지는 문제를 줄입니다.

`agent-go/Dockerfile`은 Go Agent와 MySQL Client 도구를 포함합니다. Agent는 중앙 관제 서버로 연결하고 Host Port를 공개하지 않습니다.

## 4. Docker Compose 실행

Credential 암호화 Key를 먼저 설정해야 합니다.

```bash
export DB_FLEETOPS_CREDENTIAL_ENCRYPTION_KEY='<Base64 32-byte key>'
docker compose up --build -d
```

기본 접속 위치는 다음과 같습니다.

```text
API         http://localhost:8080
Worker      http://localhost:8081
Prometheus  http://localhost:9090
Grafana     http://localhost:3000
```

전체 종료는 다음 명령을 사용합니다.

```bash
docker compose down
```

Volume까지 삭제하면 Metadata DB와 Agent 데이터도 사라지므로 `docker compose down -v`는 초기화가 필요할 때만 사용해야 합니다.

## 5. 상태 확인

Spring Actuator는 다음 Endpoint를 제공합니다.

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
/actuator/prometheus
```

Liveness는 Process가 동작하는지 확인합니다. Readiness는 요청을 처리할 준비가 됐는지 확인하며 DB 상태도 포함합니다.

Docker와 Kubernetes는 이 Endpoint를 상태 확인에 사용합니다. Kubernetes는 Readiness 실패 시 Service 대상에서 Pod를 빼고, Liveness가 반복 실패하면 Container를 다시 시작합니다.

## 6. 수집하는 운영 지표

`FleetOpsMetricsBinder`는 현재 장부의 상태별 개수를 Gauge로 제공합니다.

```text
dbfleetops_operation_jobs{status="..."}
dbfleetops_operation_tasks{status="..."}
dbfleetops_agents{status="..."}
dbfleetops_restore_verifications{status="..."}
```

Prometheus는 API와 Worker의 `/actuator/prometheus`를 15초마다 수집합니다. Grafana의 `DB FleetOps Overview` Dashboard는 Process 상태와 Operation 관련 지표를 보여줍니다.

Gauge는 현재 개수만 보여 줍니다. 처리량, 실행 시간과 오류 증가량을 분석하려면 Counter와 Timer가 추가로 필요합니다.

## 7. Worker 종료 처리

Worker가 종료 신호를 받으면 새 Job 가져오기를 중단하고 Spring의 graceful shutdown을 기다립니다.

```text
종료 신호
  → WorkerState를 종료 중으로 변경
  → 새 Job 가져오기 중단
  → 진행 중 요청 종료 대기
  → 제한 시간이 지나면 Process 종료
```

Compose의 Worker 종료 유예 시간은 70초입니다. Kubernetes도 `terminationGracePeriodSeconds=70`과 `preStop` 대기 시간을 사용합니다.

Job 실행권이 만료되면 별도 만료 처리기가 Job과 연결 Task를 확인하므로, Worker가 종료됐다는 이유만으로 같은 현장 Task를 즉시 다시 만들지 않습니다.

## 8. Docker Compose Smoke Test

다음 스크립트는 실행 중인 환경의 기본 연결 상태를 확인합니다.

```bash
./scripts/smoke-test-compose.sh
```

확인 항목은 다음과 같습니다.

- API와 Worker Health 응답
- API와 Worker Prometheus Endpoint
- Prometheus Health와 수집 Query
- Grafana Health
- Grafana Prometheus Data Source
- `DB FleetOps Overview` Dashboard 등록

이 검사는 구성요소가 연결됐는지 확인하는 Smoke Test입니다. 실제 백업, 설정 변경과 장애 복구가 성공한다는 의미는 아닙니다.

## 9. Kubernetes 예제 구성

`deploy/k8s/base`에는 Namespace, ConfigMap, Secret, Metadata MySQL, API와 Worker 예제가 있습니다. `deploy/k8s/observability`에는 Prometheus와 Grafana 예제가 있습니다.

Manifest 렌더링은 다음처럼 확인할 수 있습니다.

```bash
kubectl kustomize deploy/k8s/base
kubectl kustomize deploy/k8s/observability
```

적용 예시는 다음과 같습니다.

```bash
kubectl apply -k deploy/k8s/base
kubectl apply -k deploy/k8s/observability
kubectl get pods -n db-fleetops
```

현재 Kubernetes 파일은 로컬 학습과 구조 확인용 예제입니다. 그대로 운영에 적용할 수 있는 완성된 배포 구성이 아닙니다.

## 10. 주요 파일 책임

| 파일 또는 코드 | 책임 |
|---|---|
| `Dockerfile` | API와 Worker Java Image 생성 |
| `agent-go/Dockerfile` | Go Agent와 MySQL 도구 Image 생성 |
| `docker-compose.yml` | 로컬 전체 구성 실행 |
| `application-api.yml` | API에서 Worker 기능 비활성화 |
| `application-worker.yml` | Worker 실행과 graceful shutdown 설정 |
| `FleetOpsMetricsBinder` | Job·Task·Agent·복원 검증 Gauge 등록 |
| `infra/prometheus/prometheus.yml` | API와 Worker 지표 수집 대상 설정 |
| `infra/grafana` | Data Source와 Dashboard 자동 등록 |
| `deploy/k8s` | Kubernetes 실행 예제 |
| `scripts/smoke-test-compose.sh` | 실행 환경 기본 연결 확인 |

## 11. 운영 적용 전에 보완할 부분

- Kubernetes Secret에는 예제 비밀번호가 들어 있으므로 외부 Secret 관리 방식으로 바꿔야 합니다.
- 현재 Kubernetes Deployment에는 `DB_FLEETOPS_CREDENTIAL_ENCRYPTION_KEY` 연결이 없어 그대로 실행하면 Credential 설정이 완전하지 않습니다.
- Kubernetes에는 Go Agent 배포 Manifest가 없습니다.
- Metadata MySQL은 단일 StatefulSet이며 고가용성, Backup과 복구 절차가 없습니다.
- `ddl-auto=update` 대신 검토 가능한 Schema Migration 절차가 필요합니다.
- Actuator의 상세 Health와 Metrics Endpoint에 대한 인증·네트워크 제한이 필요합니다.
- TLS, Ingress, 인증서, NetworkPolicy와 PodDisruptionBudget이 없습니다.
- Prometheus와 Grafana의 장기 보관, Alert 규칙과 고가용성은 구성하지 않았습니다.
- API·Worker의 실제 부하, 수평 확장과 종료 중 장기 Job 동작은 별도 환경에서 검증해야 합니다.
