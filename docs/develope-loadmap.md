# DB FleetOps 개발 로드맵

## Phase 1. 단일 MySQL 연결 점검

- Spring Boot 프로젝트 생성
- 환경변수 설정
- MySQL 연결
- SELECT 1 실행
- Health API 구현
- 연결 오류 분류
- 단위 테스트

## Phase 2. DB Inventory

- Metadata DB 구성
- MySQL 인스턴스 등록
- 인스턴스 조회
- 인스턴스 수정
- 인스턴스 비활성화
- 등록된 인스턴스 연결 테스트

## Phase 3. MySQL 진단

- DB 버전
- Uptime
- Connection 수
- Connection 사용률
- 장기 실행 Transaction
- Lock Wait
- Slow Query

## Phase 4. Operation Job

- Job 생성
- Job 상태 전이
- Worker
- Lease
- Retry
- Idempotency
- Audit Log

## Phase 5. Agent

- Go Agent 등록
- Heartbeat
- Task Long Polling
- Linux 상태 수집
- 백업 작업

## Phase 6. 배포와 관측성

- Docker Compose
- Kubernetes
- Actuator
- Prometheus
- Grafana
- Readiness와 Liveness