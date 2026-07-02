# DB FleetOps 프로젝트 개요

## 1. 프로젝트 목적

DB FleetOps는 여러 데이터베이스의 상태를 한 곳에서 확인하고 운영 작업을 관리하기 위한 DBMS 운영 관리 플랫폼입니다.

현재 단계에서는 전체 기능을 한 번에 구현하지 않고, 단일 MySQL 인스턴스의 연결 상태를 점검하는 기능부터 구현했습니다. 초기 범위를 작게 잡은 이유는 DB 연결, 오류 처리, API 응답, 로그, 테스트 구조와 같은 기반을 먼저 안정적으로 만들기 위해서입니다.

## 2. 현재 구현 범위

- Spring Boot 기반 REST API 서버 구성
- 환경변수 기반 MySQL 접속정보 관리
- MySQL 연결 및 `SELECT 1` 상태 점검
- DB 상태를 `UP`, `DOWN`으로 구분
- 인증 실패, 연결 거부, 시간초과, DNS 오류 분류
- DB 상태 조회 REST API 제공
- RFC 9457 기반 공통 오류 응답 적용
- 요청별 `X-Request-ID` 생성 및 추적
- HTTP 요청 완료 로그 기록
- DB Health 점검 결과 로그 기록
- 단위 테스트와 MySQL 통합 테스트 분리

## 3. 현재 API

```http
GET /api/v1/databases/default/health
```

정상 연결 시 다음 응답을 반환합니다.

```json
{
  "databaseType": "MYSQL",
  "host": "127.0.0.1",
  "port": 3306,
  "status": "UP",
  "latencyMs": 12,
  "checkedAt": "2026-06-29T19:20:00+09:00",
  "errorCode": null,
  "message": "Database connection is available."
}
```

관리 대상 DB에 연결할 수 없는 경우에도 API 요청 자체가 정상 처리되었다면 HTTP 200을 반환합니다.

```json
{
  "databaseType": "MYSQL",
  "host": "127.0.0.1",
  "port": 3306,
  "status": "DOWN",
  "latencyMs": 8,
  "checkedAt": "2026-06-29T19:21:00+09:00",
  "errorCode": "CONNECTION_REFUSED",
  "message": "Database connection was refused."
}
```

## 4. 기술 스택

- Java 21
- Spring Boot 3.5
- Gradle
- MySQL 8.4
- Docker Compose
- JUnit 5
- Mockito
- AssertJ
- MockMvc
- Logback

## 5. 패키지 구조

```text
com.dbfleetops
├── adapter
│   └── mysql
├── common
│   ├── error
│   └── web
├── config
└── health
    ├── api
    ├── application
    ├── domain
    └── port
```

기능 중심 패키지 구조를 사용했습니다. Health 기능 안에서 API, Application, Domain, Port를 구분했습니다.

장점은 관련 코드를 한 범위에서 찾기 쉽다는 점입니다. 단점은 초기에는 디렉토리가 많아 보일 수 있다는 점입니다.

## 6. 확장 방향

현재는 `default`라는 단일 DB만 점검합니다. 이후에는 DB Inventory를 추가하여 다음 형태로 확장할 예정입니다.

```http
GET /api/v1/databases/{databaseId}/health
```

현재 구현은 이후 확장을 위한 기준을 만드는 데 초점을 두었습니다.
