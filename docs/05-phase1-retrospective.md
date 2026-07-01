# Phase 1 구현 회고

## 1. 구현한 내용

Phase 1에서는 단일 MySQL 인스턴스의 상태를 확인하는 최소 기능을 구현했습니다.

단순 JDBC 연결 외에 환경변수 설정, Adapter 분리, 오류 분류, 표준 오류 응답, Request ID, 로그, 테스트 분리까지 구성했습니다.

## 2. 구현 중 발생한 문제

### 패키지와 클래스 경로 불일치

패키지 구조를 변경하면서 테스트가 `@SpringBootConfiguration`을 찾지 못했습니다.

파일 경로, `package` 선언, import, Main Application 위치를 함께 맞춰야 한다는 점을 확인했습니다.

### Docker Volume과 환경변수 불일치

`.env`의 비밀번호를 바꿔도 기존 Volume의 MySQL 계정 비밀번호는 바뀌지 않았습니다.

```bash
docker compose down -v
docker compose up -d
```

환경변수 변경과 DB 내부 계정 변경은 별개라는 점을 확인했습니다.

### Spring 버전에 따른 Override 메서드 차이

`ResponseEntityExceptionHandler`의 메서드 이름이 프로젝트의 Spring Framework 버전과 달라 컴파일 오류가 발생했습니다.

실제 의존성 버전의 API를 확인해 다음 메서드를 사용했습니다.

```java
handleNoResourceFoundException(...)
handleNoHandlerFoundException(...)
```

### WebMvcTest에서 테스트 Controller 미등록

테스트 Controller가 등록되지 않아 모든 요청이 정적 리소스 요청으로 처리되고 404가 반환됐습니다.

테스트 Controller를 별도 파일로 분리하고 대상을 명시했습니다.

```java
@WebMvcTest(controllers = TestExceptionController.class)
```

## 3. 잘한 결정

### 기능 범위를 작게 시작한 점

단일 MySQL Health Check부터 시작해 오류 처리, 로그, 테스트 구조를 작은 범위에서 수정할 수 있었습니다.

### DB 상태와 API 상태를 분리한 점

DB가 DOWN이더라도 상태 확인이 정상 처리되면 HTTP 200을 반환했습니다. 플랫폼 장애와 대상 DB 장애를 구분할 수 있습니다.

### 오류 원인을 세분화한 점

연결 실패를 인증 실패, 연결 거부, 시간초과, DNS 오류로 나눴습니다. 운영자가 다음 행동을 판단할 수 있는 정보가 되었습니다.

### 단위 테스트와 통합 테스트를 분리한 점

일반 테스트는 빠르게 실행하고 실제 MySQL 테스트는 별도 Task로 실행했습니다.

## 4. 아쉬운 점

### 단일 대상 DB가 설정에 고정되어 있음

현재는 `default` DB 하나만 점검합니다. 여러 DB를 관리하려면 Inventory와 Credential 저장 구조가 필요합니다.

### 요청마다 JDBC 연결을 새로 생성함

Health Check 목적에는 단순하지만 대상 DB와 요청량이 늘면 연결 비용과 Timeout 관리가 중요해집니다.

### 로그가 JSON 형식이 아님

현재 Key-Value 문자열이며 로그 수집 시스템 연동 시 구조화 로깅 보완이 필요합니다.

### 비동기 점검 구조가 없음

현재는 API 요청 시점에 DB를 직접 점검합니다. 대상 DB가 많아지면 Worker가 주기적으로 점검하고 API는 최신 결과를 조회하는 구조가 필요합니다.

## 5. 다음 구현 계획

다음 단계에서는 DB Inventory 기능을 구현할 예정입니다.

- 관리 대상 DB 등록
- DB 목록 및 상세 조회
- DB 수정 및 비활성화
- DBMS 종류별 Adapter 선택
- `databaseId` 기반 Health Check
- Credential과 일반 메타데이터 분리
- 점검 결과 저장

```http
GET /api/v1/databases/{databaseId}/health
```

Phase 1에서 만든 오류 처리, Request ID, 로그, 테스트 구조는 이후 기능에서도 공통 기반으로 사용할 예정입니다.
