# DB FleetOps 배포 및 관측성 구성 정리

## 1. 배경

DB FleetOps는 여러 데이터베이스를 등록하고, 상태 점검, 작업 실행, 백업, 복원 검증, 설정 점검 등을 수행하는 DB 운영 관리 플랫폼입니다.

초기에는 Spring Boot 애플리케이션과 Go Agent를 로컬에서 직접 실행하는 방식으로 개발을 진행했습니다. 그러나 운영 관리 플랫폼의 성격상 단순히 API가 동작하는지만 확인해서는 부족했습니다.

운영 환경에서는 다음과 같은 질문에 답할 수 있어야 합니다.

```text
애플리케이션이 정상적으로 살아 있는가?
DB 연결이 가능한가?
Worker가 작업을 처리할 준비가 되어 있는가?
Job Queue가 밀리고 있는가?
Agent가 정상적으로 heartbeat를 보내고 있는가?
백업이나 복원 검증 실패가 증가하고 있는가?
Pod가 종료될 때 작업 일관성이 깨지지 않는가?
```

따라서 이번 작업에서는 단순 실행 환경을 넘어, Docker Compose와 Kubernetes 기반으로 배포 구조를 정리하고, Actuator, Prometheus, Grafana를 이용해 관측 가능한 형태로 확장했습니다.

이번 작업의 핵심 방향은 다음과 같습니다.

```text
1. 같은 애플리케이션 이미지를 API와 Worker 역할로 분리 실행
2. Actuator 기반 health/readiness/liveness endpoint 제공
3. Prometheus가 API와 Worker의 메트릭을 수집
4. Grafana에서 기본 Dashboard를 자동 구성
5. Kubernetes에서 ConfigMap, Secret, Deployment, StatefulSet, Service 구조로 배포
6. Worker 종료 시 graceful shutdown을 적용하여 작업 일관성 보완
7. Smoke Test를 통해 Compose 환경의 최소 정상 동작을 자동 확인
```

---

## 2. 전체 구성 개요

이번 구성은 크게 두 가지 실행 환경으로 나누어 정리했습니다.

```text
Docker Compose
  - 로컬 개발 및 빠른 통합 테스트용
  - API, Worker, Metadata MySQL, Target MySQL, Agent, Prometheus, Grafana 실행

Kubernetes / minikube
  - Kubernetes 배포 구조 학습 및 검증용
  - Namespace, ConfigMap, Secret, Deployment, StatefulSet, Service, Probe 적용
```

전체 구조를 단순화하면 다음과 같습니다.

```text
사용자 / 운영자
  ↓
db-fleetops-api
  ↓
metadata-mysql

db-fleetops-worker
  ↓
operation_job / operation_task 처리
  ↓
metadata-mysql

db-fleet-agent
  ↓
Control Plane polling
  ↓
Target DB 작업 수행

Prometheus
  ↓ scrape
API / Worker /actuator/prometheus

Grafana
  ↓ query
Prometheus
```

---

## 3. Dockerfile 구성

### 3.1 Spring Boot Dockerfile

Spring Boot 애플리케이션은 하나의 JAR로 빌드하고, 같은 이미지를 API와 Worker에서 함께 사용하도록 구성했습니다.

```dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ARG JAR_FILE=build/libs/*.jar

COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

설계 근거는 다음과 같습니다.

```text
1. JRE 이미지만 사용하여 런타임 이미지를 단순화했습니다.
2. 같은 이미지를 API와 Worker가 공유하도록 하여 빌드 산출물을 하나로 유지했습니다.
3. JAVA_OPTS를 환경변수로 받아 실행 환경별 JVM 옵션을 조정할 수 있게 했습니다.
4. Docker Compose healthcheck에서 curl을 사용하기 위해 curl을 포함했습니다.
```

처음부터 멀티스테이지 빌드로 구성할 수도 있지만, 현재 단계에서는 로컬에서 `./gradlew bootJar`를 명시적으로 수행한 뒤 이미지를 만드는 방식이 더 단순하고 디버깅이 쉽습니다.

---

## 4. API와 Worker Profile 분리

DB FleetOps는 하나의 Spring Boot 애플리케이션이지만, 실행 역할은 API와 Worker로 나누었습니다.

```text
API
  - 외부 REST 요청 처리
  - 조회/등록/요청 API 제공
  - profile: docker,api

Worker
  - 내부 Job 처리
  - operation_job claim
  - backup task 생성
  - configuration check 실행
  - profile: docker,worker
```

API profile 설정 예시는 다음과 같습니다.

```yaml
db-fleetops:
  worker:
    enabled: false

spring:
  main:
    web-application-type: servlet
```

Worker profile 설정은 다음과 같습니다.

```yaml
db-fleetops:
  worker:
    enabled: true
    shutdown:
      stop-claiming-on-shutdown: true

spring:
  main:
    web-application-type: servlet

  lifecycle:
    timeout-per-shutdown-phase: 60s

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful
```

이렇게 분리한 이유는 다음과 같습니다.

```text
1. 코드베이스는 하나로 유지하면서 실행 역할만 분리할 수 있습니다.
2. API와 Worker를 독립적으로 scale-out할 수 있습니다.
3. Worker 장애가 API replica 수와 직접 묶이지 않습니다.
4. Kubernetes에서 Deployment를 분리하기 쉽습니다.
5. 포트폴리오 관점에서 Modular Monolith를 운영 역할별로 나누는 설계를 설명하기 좋습니다.
```

---

## 5. Actuator와 Prometheus Endpoint

운영 환경에서는 애플리케이션이 살아 있는지 단순히 프로세스 기준으로만 판단하면 부족합니다.

Spring Boot Actuator를 사용하여 다음 endpoint를 노출했습니다.

```text
/actuator/health
/actuator/health/readiness
/actuator/health/liveness
/actuator/prometheus
```

설정은 다음과 같습니다.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,db

  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true

  prometheus:
    metrics:
      export:
        enabled: true
```

각 endpoint의 의미는 다음과 같습니다.

```text
/actuator/health
  - 애플리케이션 전체 health 상태 확인

/actuator/health/readiness
  - 요청을 받을 준비가 되었는지 확인
  - DB 연결 상태 등을 포함할 수 있음

/actuator/health/liveness
  - 프로세스가 살아 있는지 확인
  - Kubernetes가 Pod 재시작 여부를 판단하는 데 사용

/actuator/prometheus
  - Prometheus가 scrape할 수 있는 메트릭 endpoint
```

Kubernetes에서는 readiness와 liveness를 분리하는 것이 중요합니다.

```text
readiness 실패
  - Pod는 살아 있지만 Service endpoint에서 제외됨
  - 트래픽을 받지 않음

liveness 실패
  - Kubernetes가 Pod를 재시작함
```

DB가 잠시 연결되지 않는다고 liveness를 실패시키면 Pod가 계속 재시작될 수 있습니다. 이런 경우는 readiness 실패로 처리하는 것이 더 적절합니다.

---

## 6. Custom Operation Metrics

Actuator 기본 메트릭만으로는 DB FleetOps의 운영 상태를 충분히 알 수 없습니다.

기본 메트릭은 다음과 같습니다.

```text
jvm_memory_used_bytes
http_server_requests_seconds_count
hikaricp_connections_active
process_cpu_usage
```

하지만 DB FleetOps에서는 다음과 같은 도메인 상태가 중요합니다.

```text
현재 QUEUED Job이 몇 개인가?
RUNNING Task가 있는가?
FAILED Job이 증가하고 있는가?
복원 검증 실패가 발생했는가?
```

이를 위해 Repository count 기반 Gauge를 추가했습니다.

예시 메트릭은 다음과 같습니다.

```text
dbfleetops_operation_jobs{status="QUEUED"}
dbfleetops_operation_jobs{status="RUNNING"}
dbfleetops_operation_tasks{status="QUEUED"}
dbfleetops_operation_tasks{status="RUNNING"}
dbfleetops_restore_verifications{status="FAILED"}
```

Gauge를 사용한 이유는 현재 상태를 표현하기에 적합하기 때문입니다.

```text
현재 대기 중인 Job 수
현재 실행 중인 Task 수
현재 실패 상태인 복원 검증 수
```

이 값들은 누적 이벤트 수가 아니라 현재 시점의 상태 수이므로 Counter보다 Gauge가 적합합니다.

또한 상태별로 메트릭 이름을 나누는 대신 status label을 사용했습니다.

```text
좋지 않은 방식:
  dbfleetops_operation_jobs_queued
  dbfleetops_operation_jobs_running
  dbfleetops_operation_jobs_failed

선택한 방식:
  dbfleetops_operation_jobs{status="QUEUED"}
  dbfleetops_operation_jobs{status="RUNNING"}
  dbfleetops_operation_jobs{status="FAILED"}
```

이 방식의 장점은 다음과 같습니다.

```text
1. 하나의 metric family로 관리할 수 있습니다.
2. PromQL에서 status별 필터링이 쉽습니다.
3. Grafana 패널에서 status별 legend 구성이 쉽습니다.
4. 새로운 상태가 추가되어도 메트릭 이름이 계속 늘어나지 않습니다.
```

---

## 7. Docker Compose Observability 구성

Docker Compose에서는 다음 서비스를 함께 실행하도록 구성했습니다.

```text
metadata-mysql
target-mysql
db-fleetops-api
db-fleetops-worker
db-fleet-agent
prometheus
grafana
```

Prometheus 설정은 다음과 같습니다.

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: "db-fleetops-api"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets:
          - "db-fleetops-api:8080"
        labels:
          application: "db-fleetops"
          component: "api"
          environment: "compose"

  - job_name: "db-fleetops-worker"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets:
          - "db-fleetops-worker:8080"
        labels:
          application: "db-fleetops"
          component: "worker"
          environment: "compose"
```

여기서 중요한 점은 target에 `localhost`를 사용하지 않는다는 것입니다.

```text
Prometheus 컨테이너 기준 localhost
  = Prometheus 자기 자신

db-fleetops-api:8080
  = Compose 네트워크 안의 API 서비스

db-fleetops-worker:8080
  = Compose 네트워크 안의 Worker 서비스
```

Grafana는 Prometheus를 datasource로 사용합니다.

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    uid: prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

Grafana도 마찬가지로 `localhost:9090`이 아니라 `prometheus:9090`을 사용해야 합니다.

---

## 8. Grafana Dashboard 구성

Grafana Dashboard는 provisioning 방식으로 구성했습니다.

```text
infra/grafana/provisioning/datasources/prometheus.yml
infra/grafana/provisioning/dashboards/dashboard-provider.yml
infra/grafana/dashboards/db-fleetops-overview.json
```

UI에서 직접 만들 수도 있지만, provisioning 파일로 관리한 이유는 다음과 같습니다.

```text
1. docker compose up만으로 같은 Dashboard를 재현할 수 있습니다.
2. Dashboard 설정을 Git으로 관리할 수 있습니다.
3. 포트폴리오에서 운영 환경 구성 의도를 설명하기 좋습니다.
4. Kubernetes ConfigMap으로 옮기기 쉽습니다.
```

기본 Dashboard에는 다음 패널을 구성했습니다.

```text
API Up
Worker Up
Queued Jobs
Running Jobs
HTTP Request Rate
JVM Memory Used
Operation Jobs by Status
Operation Tasks by Status
Restore Verification by Status
```

이 Dashboard를 통해 최소한 다음 질문에 답할 수 있습니다.

```text
API가 살아 있는가?
Worker가 살아 있는가?
Job Queue가 밀리는가?
Task가 실행 중인가?
요청량이 발생하는가?
JVM 메모리가 증가하고 있는가?
복원 검증 실패가 있는가?
```

---

## 9. Kubernetes 구성 개요

Docker Compose는 로컬 개발과 통합 테스트에 적합하지만, 실제 운영 구조를 설명하기 위해 Kubernetes manifest도 추가했습니다.

Kubernetes 리소스는 크게 base와 observability로 나누었습니다.

```text
deploy/k8s/base
  - 애플리케이션 실행에 필요한 기본 리소스

deploy/k8s/observability
  - Prometheus와 Grafana 리소스
```

base 구조는 다음과 같습니다.

```text
deploy/k8s/base
├── namespace.yaml
├── configmap.yaml
├── secret.yaml
├── metadata-mysql-service.yaml
├── metadata-mysql-statefulset.yaml
├── api-deployment.yaml
├── api-service.yaml
├── worker-deployment.yaml
├── worker-service.yaml
└── kustomization.yaml
```

observability 구조는 다음과 같습니다.

```text
deploy/k8s/observability
├── prometheus-configmap.yaml
├── prometheus-deployment.yaml
├── prometheus-service.yaml
├── grafana-configmap.yaml
├── grafana-deployment.yaml
├── grafana-service.yaml
└── kustomization.yaml
```

---

## 10. Kubernetes 주요 개념 정리

### 10.1 Namespace

Namespace는 Kubernetes 리소스를 논리적으로 나누는 공간입니다.

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: db-fleetops
```

`db-fleetops` 관련 리소스를 하나의 namespace에 넣으면 조회와 삭제가 쉬워집니다.

```bash
kubectl get all -n db-fleetops
kubectl delete -k deploy/k8s/base
```

---

### 10.2 ConfigMap

ConfigMap은 민감하지 않은 설정을 관리합니다.

```text
SPRING_PROFILES_ACTIVE
DB URL
JPA dialect
Actuator 설정
Timezone
JVM option
```

예시는 다음과 같습니다.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: db-fleetops-config
  namespace: db-fleetops
data:
  TZ: "Asia/Seoul"
  SPRING_PROFILES_ACTIVE_API: "docker,api"
  SPRING_PROFILES_ACTIVE_WORKER: "docker,worker"
  SERVER_PORT: "8080"
  DB_FLEETOPS_DATASOURCE_URL: "jdbc:mysql://metadata-mysql:3306/db_fleetops?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
```

여기서 `metadata-mysql`은 Pod 이름이 아니라 Service 이름입니다.

```text
API Pod
  ↓
metadata-mysql:3306
  ↓
metadata-mysql Service
  ↓
MySQL Pod
```

---

### 10.3 Secret

Secret은 비밀번호 같은 민감 정보를 관리합니다.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-fleetops-secret
  namespace: db-fleetops
type: Opaque
stringData:
  DB_FLEETOPS_DATASOURCE_PASSWORD: "dbfleetpw"
  METADATA_MYSQL_ROOT_PASSWORD: "rootpw"
  METADATA_MYSQL_USERNAME: "dbfleet"
  METADATA_MYSQL_PASSWORD: "dbfleetpw"
```

학습 및 포트폴리오 목적에서는 `stringData`를 사용했지만, 실제 운영에서는 Secret을 Git에 평문으로 두면 안 됩니다.

운영에서는 다음과 같은 방식을 고려해야 합니다.

```text
External Secrets Operator
Sealed Secrets
Vault
AWS Secrets Manager
GCP Secret Manager
Azure Key Vault
```

---

### 10.4 Deployment

Deployment는 API나 Worker처럼 상태를 직접 저장하지 않는 애플리케이션을 실행하는 데 사용합니다.

```text
Deployment 역할
  - 원하는 Pod 개수 유지
  - Pod 장애 시 재생성
  - Rolling Update 지원
  - ReplicaSet 관리
```

API와 Worker는 같은 이미지를 사용하지만 profile을 다르게 주어 실행합니다.

```text
API
  image: db-fleetops-api:local
  SPRING_PROFILES_ACTIVE=docker,api

Worker
  image: db-fleetops-api:local
  SPRING_PROFILES_ACTIVE=docker,worker
```

이 구조의 장점은 다음과 같습니다.

```text
1. 빌드 산출물은 하나로 유지됩니다.
2. 실행 역할은 분리됩니다.
3. API와 Worker를 독립적으로 scale-out할 수 있습니다.
4. Worker 장애가 API replica와 직접 묶이지 않습니다.
```

---

### 10.5 StatefulSet

MySQL은 데이터를 저장하는 상태가 있는 컴포넌트입니다.

따라서 Deployment가 아니라 StatefulSet을 사용했습니다.

```text
Deployment
  - stateless app에 적합
  - API, Worker

StatefulSet
  - stateful app에 적합
  - MySQL, Kafka, Redis, Elasticsearch
```

Metadata MySQL은 다음 구조로 구성했습니다.

```text
metadata-mysql StatefulSet
  ↓
PVC
  ↓
/var/lib/mysql
```

다만 실제 운영에서는 Kubernetes 내부 단일 MySQL보다 관리형 DB를 사용하는 것이 더 적절합니다.

```text
포트폴리오 / 로컬 학습
  - MySQL StatefulSet

실제 운영
  - AWS RDS
  - Cloud SQL
  - Aurora
  - 별도 HA MySQL
```

---

### 10.6 Service

Kubernetes에서 Pod는 언제든 재생성될 수 있고 IP도 바뀔 수 있습니다.

따라서 다른 Pod가 특정 Pod IP를 직접 바라보면 안 됩니다.

Service는 안정적인 네트워크 이름과 포트를 제공합니다.

```text
Service
  - 고정된 이름 제공
  - 고정된 포트 제공
  - selector로 대상 Pod를 찾음
```

예시는 다음과 같습니다.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: db-fleetops-api
  namespace: db-fleetops
spec:
  type: ClusterIP
  selector:
    app: db-fleetops-api
    component: api
  ports:
    - name: http
      port: 8080
      targetPort: http
```

`ClusterIP`는 클러스터 내부에서만 접근 가능한 Service입니다.

로컬에서 접근하려면 `port-forward`를 사용합니다.

```bash
kubectl -n db-fleetops port-forward svc/db-fleetops-api 8080:8080
```

---

### 10.7 Kustomize

Kustomize는 여러 Kubernetes YAML을 한 번에 묶어서 적용할 수 있게 해줍니다.

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - namespace.yaml
  - configmap.yaml
  - secret.yaml
  - metadata-mysql-service.yaml
  - metadata-mysql-statefulset.yaml
  - api-deployment.yaml
  - api-service.yaml
  - worker-deployment.yaml
  - worker-service.yaml
```

이 파일이 있으면 다음 명령 하나로 전체를 적용할 수 있습니다.

```bash
kubectl apply -k deploy/k8s/base
```

---

## 11. minikube 사용 절차

이번 Kubernetes 테스트는 minikube를 기준으로 진행했습니다.

minikube는 로컬 개발 환경에서 Kubernetes 단일 노드 클러스터를 쉽게 실행할 수 있게 해줍니다.

### 11.1 설치

```bash
brew install kubectl
brew install minikube
```

설치 확인:

```bash
kubectl version --client
minikube version
```

---

### 11.2 minikube 시작

처음에는 다음과 같이 시작했습니다.

```bash
minikube start \
  --driver=docker \
  --cpus=4 \
  --memory=6144 \
  --disk-size=30g
```

각 옵션의 의미는 다음과 같습니다.

```text
--driver=docker
  Docker 위에서 minikube 클러스터 실행

--cpus=4
  minikube에 CPU 4개 할당

--memory=6144
  minikube에 메모리 6GB 할당

--disk-size=30g
  minikube 디스크 30GB 할당
```

메모리를 8192MB로 주려고 했지만 Docker Desktop에 할당된 메모리가 8005MB여서 실패했습니다.

따라서 현재 로컬 환경에서는 6144MB로 조정했습니다.

---

### 11.3 minikube 상태 확인

```bash
minikube status
```

정상 예시는 다음과 같습니다.

```text
host: Running
kubelet: Running
apiserver: Running
kubeconfig: Configured
```

---

### 11.4 kubectl context 설정

`kubectl`이 minikube 클러스터를 바라보도록 context를 설정합니다.

```bash
minikube update-context
kubectl config use-context minikube
kubectl config current-context
```

정상 결과:

```text
minikube
```

노드 확인:

```bash
kubectl get nodes
```

정상 예시:

```text
NAME       STATUS   ROLES           AGE   VERSION
minikube   Ready    control-plane   ...
```

만약 다음 에러가 나오면 context가 설정되지 않은 것입니다.

```text
error: current-context is not set
```

이 경우 다시 아래를 실행합니다.

```bash
minikube update-context
kubectl config use-context minikube
kubectl get nodes
```

---

### 11.5 minikube stop 후 재시작

minikube를 중지한 경우:

```bash
minikube stop
```

다시 시작:

```bash
minikube start
```

또는 처음 옵션을 다시 명시해도 됩니다.

```bash
minikube start \
  --driver=docker \
  --cpus=4 \
  --memory=6144 \
  --disk-size=30g
```

재시작 후 확인:

```bash
minikube status
minikube update-context
kubectl config use-context minikube
kubectl get nodes
```

`minikube stop`은 클러스터를 삭제하는 것이 아닙니다.

```text
minikube stop
  - 클러스터를 잠깐 중지
  - 기존 리소스는 보통 유지

minikube delete
  - 클러스터 자체 삭제
  - 기존 리소스도 사라짐
```

---

## 12. minikube 이미지 처리

Kubernetes manifest에서는 다음 이미지를 사용합니다.

```yaml
image: db-fleetops-api:local
imagePullPolicy: IfNotPresent
```

로컬 Docker에서 이미지를 빌드해도 minikube가 바로 볼 수 있는 것은 아닙니다.

따라서 이미지를 minikube로 로드해야 합니다.

```bash
./gradlew clean test bootJar
docker build -t db-fleetops-api:local .
minikube image load db-fleetops-api:local
```

확인:

```bash
minikube image ls | grep db-fleetops-api
```

`imagePullPolicy: IfNotPresent`를 사용한 이유는 다음과 같습니다.

```text
IfNotPresent
  - minikube 내부에 이미지가 있으면 그것을 사용

Always
  - 원격 registry에서 항상 pull하려고 함
  - db-fleetops-api:local은 원격 registry에 없으므로 실패 가능성이 높음
```

---

## 13. Kubernetes 적용 명령 정리

### 13.1 base manifest 검증

```bash
kubectl kustomize deploy/k8s/base
```

```bash
kubectl apply --dry-run=client -k deploy/k8s/base
```

### 13.2 base manifest 적용

```bash
kubectl apply -k deploy/k8s/base
```

### 13.3 리소스 확인

```bash
kubectl get all -n db-fleetops
```

```bash
kubectl get pods -n db-fleetops
```

```bash
kubectl get svc -n db-fleetops
```

```bash
kubectl get pvc -n db-fleetops
```

### 13.4 Pod 상태 watch

```bash
kubectl get pods -n db-fleetops -w
```

### 13.5 로그 확인

API 로그:

```bash
kubectl logs -n db-fleetops deploy/db-fleetops-api
```

Worker 로그:

```bash
kubectl logs -n db-fleetops deploy/db-fleetops-worker
```

MySQL 로그:

```bash
kubectl logs -n db-fleetops statefulset/metadata-mysql
```

로그를 계속 보려면:

```bash
kubectl logs -f -n db-fleetops deploy/db-fleetops-api
```

### 13.6 Pod 상세 확인

```bash
kubectl describe pod -n db-fleetops <pod-name>
```

Pod 이름 확인:

```bash
kubectl get pods -n db-fleetops
```

### 13.7 Service endpoint 확인

```bash
kubectl get endpoints -n db-fleetops
```

Service가 Pod를 제대로 찾지 못하면 endpoint가 비어 있을 수 있습니다.

이 경우 selector와 Pod label을 확인해야 합니다.

```bash
kubectl get pods -n db-fleetops --show-labels
```

---

## 14. port-forward 명령 정리

Kubernetes의 ClusterIP Service는 로컬 브라우저에서 바로 접근할 수 없습니다.

로컬 테스트에서는 `port-forward`를 사용합니다.

### 14.1 API

```bash
kubectl -n db-fleetops port-forward svc/db-fleetops-api 8080:8080
```

확인:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/prometheus | head
```

### 14.2 Worker

```bash
kubectl -n db-fleetops port-forward svc/db-fleetops-worker 8081:8080
```

확인:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus | head
```

### 14.3 Prometheus

```bash
kubectl -n db-fleetops port-forward svc/prometheus 9090:9090
```

확인:

```bash
curl http://localhost:9090/-/healthy
curl http://localhost:9090/-/ready
curl -s "http://localhost:9090/api/v1/query?query=up"
```

브라우저:

```text
http://localhost:9090/targets
```

### 14.4 Grafana

```bash
kubectl -n db-fleetops port-forward svc/grafana 3000:3000
```

브라우저:

```text
http://localhost:3000
```

로그인:

```text
ID: admin
PW: admin
```

Dashboard 경로:

```text
Dashboards
  ↓
DB FleetOps
  ↓
DB FleetOps Overview
```

---

## 15. Kubernetes Observability 구성

Kubernetes에서도 Prometheus와 Grafana를 별도 manifest로 구성했습니다.

Prometheus는 ConfigMap에 설정 파일을 넣고, Deployment에서 이를 mount합니다.

```text
prometheus-config ConfigMap
  ↓
/etc/prometheus/prometheus.yml
  ↓
Prometheus container
```

Prometheus scrape 설정은 다음과 같습니다.

```yaml
scrape_configs:
  - job_name: "db-fleetops-api"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets:
          - "db-fleetops-api:8080"

  - job_name: "db-fleetops-worker"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets:
          - "db-fleetops-worker:8080"
```

Grafana는 Prometheus datasource와 Dashboard JSON을 ConfigMap으로 주입했습니다.

```text
grafana-provisioning ConfigMap
  ├── datasource-prometheus.yml
  ├── dashboard-provider.yml
  └── db-fleetops-overview.json
```

Kubernetes에서 observability manifest 적용 명령은 다음과 같습니다.

```bash
kubectl kustomize deploy/k8s/observability
kubectl apply --dry-run=client -k deploy/k8s/observability
kubectl apply -k deploy/k8s/observability
```

확인:

```bash
kubectl get all -n db-fleetops
kubectl get pods -n db-fleetops
```

---

## 16. Worker Graceful Shutdown 설계

Worker는 종료될 때 특별한 처리가 필요합니다.

API 서버는 요청을 받지 않으면 되지만, Worker는 background job을 claim하고 처리합니다.

문제는 다음과 같습니다.

```text
Worker Pod 종료 시작
  ↓
종료 직전에 새 Job claim
  ↓
Job 상태 RUNNING으로 변경
  ↓
Pod 종료
  ↓
Job이 RUNNING 상태로 남을 수 있음
```

이를 막기 위해 애플리케이션 내부에 `WorkerShutdownState`를 도입했습니다.

종료 흐름은 다음과 같습니다.

```text
Kubernetes가 SIGTERM 전송
  ↓
Spring ContextClosedEvent 발생
  ↓
WorkerShutdownState.shuttingDown = true
  ↓
OperationWorkerService.claimJob()에서 새 claim 차단
```

핵심 방어는 `claimJob()`에 있습니다.

```java
if (workerShutdownState.isShuttingDown()) {
    return ClaimJobResponse.empty();
}
```

또한 Job 조회 후 `job.start()` 직전에도 한 번 더 확인했습니다.

```java
if (workerShutdownState.isShuttingDown()) {
    return ClaimJobResponse.empty();
}
```

두 번 확인한 이유는 race condition을 줄이기 위해서입니다.

```text
첫 번째 확인
  - DB 조회 전 차단

두 번째 확인
  - Job 조회 후 RUNNING 전환 직전 차단
```

가장 중요한 상태 전이는 다음입니다.

```text
QUEUED → RUNNING
```

종료 중에는 이 전이를 막아야 합니다.

---

## 17. Graceful Shutdown 설정

Spring Boot Worker profile에는 다음 설정을 추가했습니다.

```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 60s

server:
  shutdown: graceful
```

Docker Compose Worker에는 다음을 추가했습니다.

```yaml
stop_grace_period: 70s
```

Kubernetes Worker Deployment에는 다음을 추가했습니다.

```yaml
terminationGracePeriodSeconds: 70
```

그리고 `preStop` hook을 추가했습니다.

```yaml
lifecycle:
  preStop:
    exec:
      command:
        - sh
        - -c
        - sleep 10
```

각 설정의 역할은 다음과 같습니다.

```text
server.shutdown=graceful
  - Spring Boot 웹 서버가 종료 시 기존 요청을 정리할 시간을 가짐

spring.lifecycle.timeout-per-shutdown-phase=60s
  - Spring Bean 종료 phase에 최대 60초 대기

stop_grace_period=70s
  - Docker Compose가 SIGTERM 후 70초까지 기다림

terminationGracePeriodSeconds=70
  - Kubernetes가 SIGTERM 후 SIGKILL 전까지 70초 대기

preStop sleep 10
  - Pod 종료 직전 endpoint 제거 및 전파 시간을 확보
```

중요한 점은 Kubernetes 설정만으로 Worker 작업 일관성이 완전히 보장되지 않는다는 것입니다.

```text
Kubernetes 설정
  - 종료 시간을 제공

애플리케이션 코드
  - 종료 중 새 Job claim을 막음
```

따라서 graceful shutdown은 외부 제어와 내부 제어가 함께 필요합니다.

---

## 18. Docker Compose Smoke Test

배포 환경이 실제로 정상 동작하는지 확인하기 위해 smoke test를 추가했습니다.

스크립트 경로:

```text
scripts/smoke-test-compose.sh
```

검증 항목은 다음과 같습니다.

```text
API /actuator/health
Worker /actuator/health
API /actuator/prometheus
Worker /actuator/prometheus
Prometheus /-/healthy
Prometheus /-/ready
Grafana /api/health
Prometheus target query
Grafana datasource
Grafana dashboard
```

실행:

```bash
./gradlew clean test bootJar
docker compose build
docker compose up -d
./scripts/smoke-test-compose.sh
```

Smoke Test는 단위 테스트와 목적이 다릅니다.

```text
단위 테스트
  - 코드 내부 로직 검증

Smoke Test
  - 실제 실행 환경이 최소한 정상적으로 떠 있는지 검증
```

이 스크립트를 통해 Docker Compose 환경에서 API, Worker, Prometheus, Grafana가 서로 정상 연결되는지 빠르게 확인할 수 있습니다.

---

## 19. 주요 트러블슈팅

### 19.1 `/actuator/prometheus`가 404인 경우

확인:

```bash
curl -i http://localhost:8080/actuator
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/actuator/prometheus
```

원인 후보:

```text
1. spring-boot-starter-actuator 의존성 누락
2. micrometer-registry-prometheus 의존성 누락
3. management.endpoints.web.exposure.include 설정 미적용
4. bootJar 또는 Docker image 재빌드 누락
```

확인:

```bash
grep -n "actuator\|prometheus\|micrometer" build.gradle
```

필요 의존성:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

---

### 19.2 readiness 때문에 Compose가 막히는 경우

Compose에서는 healthcheck를 `/actuator/health/readiness`가 아니라 `/actuator/health`로 두었습니다.

```yaml
healthcheck:
  test:
    [
      "CMD-SHELL",
      "curl -fsS http://127.0.0.1:8080/actuator/health > /dev/null || exit 1"
    ]
```

이유는 Compose 단계에서는 readiness group 설정 문제로 전체 서비스가 불필요하게 막힐 수 있기 때문입니다.

Kubernetes에서는 readiness/liveness를 분리해서 사용합니다.

```text
Compose
  - /actuator/health

Kubernetes
  - /actuator/health/readiness
  - /actuator/health/liveness
```

---

### 19.3 kubectl context가 없는 경우

에러:

```text
error: current-context is not set
```

해결:

```bash
minikube status
minikube update-context
kubectl config use-context minikube
kubectl config current-context
kubectl get nodes
```

---

### 19.4 kubectl이 localhost:8080을 바라보는 경우

에러:

```text
failed to download openapi:
Get "http://localhost:8080/openapi/v2?timeout=32s":
connect: connection refused
```

이 경우 kubectl이 Kubernetes API Server가 아니라 `localhost:8080`을 바라보고 있는 것입니다.

해결:

```bash
minikube update-context
kubectl config use-context minikube
kubectl get nodes
```

정상 확인:

```bash
kubectl cluster-info
```

---

### 19.5 ImagePullBackOff

확인:

```bash
kubectl get pods -n db-fleetops
```

원인:

```text
minikube 내부에 db-fleetops-api:local 이미지가 없음
```

해결:

```bash
docker build -t db-fleetops-api:local .
minikube image load db-fleetops-api:local
kubectl rollout restart deployment -n db-fleetops db-fleetops-api
kubectl rollout restart deployment -n db-fleetops db-fleetops-worker
```

---

### 19.6 CrashLoopBackOff

확인:

```bash
kubectl logs -n db-fleetops deploy/db-fleetops-api
kubectl describe pod -n db-fleetops <pod-name>
```

원인 후보:

```text
DB 연결 실패
ConfigMap key 오타
Secret key 오타
Hibernate dialect 문제
Actuator readiness endpoint 404
MySQL 준비 지연
```

---

## 20. Kubernetes 명령어 모음

### 20.1 minikube 기본

```bash
minikube start \
  --driver=docker \
  --cpus=4 \
  --memory=6144 \
  --disk-size=30g
```

```bash
minikube status
```

```bash
minikube stop
```

```bash
minikube start
```

```bash
minikube delete
```

```bash
minikube image load db-fleetops-api:local
```

```bash
minikube image ls | grep db-fleetops-api
```

---

### 20.2 kubectl context

```bash
kubectl config get-contexts
```

```bash
kubectl config current-context
```

```bash
minikube update-context
```

```bash
kubectl config use-context minikube
```

```bash
kubectl get nodes
```

```bash
kubectl cluster-info
```

---

### 20.3 manifest 검증 및 적용

```bash
kubectl kustomize deploy/k8s/base
```

```bash
kubectl apply --dry-run=client -k deploy/k8s/base
```

```bash
kubectl apply -k deploy/k8s/base
```

```bash
kubectl kustomize deploy/k8s/observability
```

```bash
kubectl apply --dry-run=client -k deploy/k8s/observability
```

```bash
kubectl apply -k deploy/k8s/observability
```

---

### 20.4 리소스 조회

```bash
kubectl get all -n db-fleetops
```

```bash
kubectl get pods -n db-fleetops
```

```bash
kubectl get svc -n db-fleetops
```

```bash
kubectl get endpoints -n db-fleetops
```

```bash
kubectl get pvc -n db-fleetops
```

```bash
kubectl get configmap -n db-fleetops
```

```bash
kubectl get secret -n db-fleetops
```

---

### 20.5 로그 확인

```bash
kubectl logs -n db-fleetops deploy/db-fleetops-api
```

```bash
kubectl logs -n db-fleetops deploy/db-fleetops-worker
```

```bash
kubectl logs -n db-fleetops statefulset/metadata-mysql
```

```bash
kubectl logs -n db-fleetops deploy/prometheus
```

```bash
kubectl logs -n db-fleetops deploy/grafana
```

```bash
kubectl logs -f -n db-fleetops deploy/db-fleetops-worker
```

---

### 20.6 상세 진단

```bash
kubectl describe pod -n db-fleetops <pod-name>
```

```bash
kubectl describe deployment -n db-fleetops db-fleetops-api
```

```bash
kubectl describe deployment -n db-fleetops db-fleetops-worker
```

```bash
kubectl describe statefulset -n db-fleetops metadata-mysql
```

```bash
kubectl get pods -n db-fleetops --show-labels
```

---

### 20.7 rollout

```bash
kubectl rollout restart deployment -n db-fleetops db-fleetops-api
```

```bash
kubectl rollout restart deployment -n db-fleetops db-fleetops-worker
```

```bash
kubectl rollout status deployment -n db-fleetops db-fleetops-api
```

```bash
kubectl rollout status deployment -n db-fleetops db-fleetops-worker
```

---

### 20.8 scale

```bash
kubectl scale deployment -n db-fleetops db-fleetops-worker --replicas=1
```

```bash
kubectl scale deployment -n db-fleetops db-fleetops-worker --replicas=2
```

```bash
kubectl scale deployment -n db-fleetops db-fleetops-api --replicas=2
```

---

### 20.9 port-forward

```bash
kubectl -n db-fleetops port-forward svc/db-fleetops-api 8080:8080
```

```bash
kubectl -n db-fleetops port-forward svc/db-fleetops-worker 8081:8080
```

```bash
kubectl -n db-fleetops port-forward svc/prometheus 9090:9090
```

```bash
kubectl -n db-fleetops port-forward svc/grafana 3000:3000
```

---

### 20.10 삭제

```bash
kubectl delete -k deploy/k8s/observability
```

```bash
kubectl delete -k deploy/k8s/base
```

전체 minikube 삭제:

```bash
minikube delete
```

---

## 21. 전체 실행 순서 요약

처음부터 minikube 기준으로 실행하면 다음 순서입니다.

```bash
brew install kubectl
brew install minikube
```

```bash
minikube start \
  --driver=docker \
  --cpus=4 \
  --memory=6144 \
  --disk-size=30g
```

```bash
minikube update-context
kubectl config use-context minikube
kubectl get nodes
```

```bash
./gradlew clean test bootJar
docker build -t db-fleetops-api:local .
minikube image load db-fleetops-api:local
```

```bash
kubectl apply --dry-run=client -k deploy/k8s/base
kubectl apply -k deploy/k8s/base
kubectl get pods -n db-fleetops -w
```

```bash
kubectl apply --dry-run=client -k deploy/k8s/observability
kubectl apply -k deploy/k8s/observability
kubectl get all -n db-fleetops
```

API 확인:

```bash
kubectl -n db-fleetops port-forward svc/db-fleetops-api 8080:8080
```

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus | head
```

Prometheus 확인:

```bash
kubectl -n db-fleetops port-forward svc/prometheus 9090:9090
```

```bash
curl http://localhost:9090/-/healthy
curl -s "http://localhost:9090/api/v1/query?query=up"
```

Grafana 확인:

```bash
kubectl -n db-fleetops port-forward svc/grafana 3000:3000
```

브라우저:

```text
http://localhost:3000
```

---

## 22. 이번 작업의 의미

이번 배포 및 관측성 구성은 단순히 Docker와 Kubernetes를 붙인 작업이 아닙니다.

DB 운영 관리 플랫폼이라는 도메인에서 실제 운영 시 필요한 질문에 답할 수 있도록 구조를 정리한 작업입니다.

```text
배포 가능성
  - Dockerfile
  - Docker Compose
  - Kubernetes manifest

운영 상태 확인
  - Actuator health
  - readiness/liveness
  - Prometheus metrics
  - Grafana dashboard

작업 일관성
  - Worker graceful shutdown
  - shutdown 중 새 Job claim 방지

재현 가능성
  - Compose provisioning
  - Kubernetes Kustomize
  - Smoke Test
```

특히 Worker graceful shutdown은 운영 관점에서 중요한 설계입니다.

Kubernetes는 Pod를 종료할 시간을 줄 수는 있지만, 애플리케이션 내부의 Job claim 로직까지 이해하지는 못합니다.

따라서 작업 일관성을 지키려면 애플리케이션 코드에서 종료 상태를 인지하고, `QUEUED → RUNNING` 전이를 차단해야 합니다.

이 점에서 이번 작업은 단순 인프라 설정이 아니라, 애플리케이션 로직과 배포 환경을 함께 고려한 운영 설계라고 볼 수 있습니다.

---

## 23. 커밋 메시지

이번 문서 커밋 메시지는 다음과 같이 정리합니다.

```bash
git add docs/deployment-and-observability.md
git commit -m "docs: add deployment and observability guide"
```

---

## 24. 이후 개선 방향

이후에는 다음 작업을 추가로 진행할 수 있습니다.

```text
1. Kubernetes Secret을 Sealed Secret 또는 External Secret으로 분리
2. Prometheus 데이터를 PVC로 영속화
3. Grafana admin password를 Secret으로 분리
4. Worker stuck task recovery 추가
5. Job lease timeout 기반 재처리 정책 추가
6. API latency, error rate, p95/p99 Dashboard 추가
7. HPA 적용
8. Ingress 추가
9. Helm chart 또는 Kustomize overlay 구성
10. CI에서 smoke test 자동 실행
```