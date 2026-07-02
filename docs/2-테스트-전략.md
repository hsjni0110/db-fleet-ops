# 테스트 전략

## 1. 테스트 범위를 나눈 이유

현재 테스트는 다음 범위로 나누었습니다.

- 오류 분류 단위 테스트
- Service 단위 테스트
- Controller 및 공통 오류 응답 MVC 테스트
- Filter 단위 테스트
- 실제 MySQL 연결 통합 테스트

각 테스트가 확인하려는 책임을 분리하면 실패 원인을 빠르게 찾을 수 있습니다.

Service 테스트에서는 실제 MySQL을 연결하지 않고 `DatabaseHealthProbe`를 Mock으로 대체합니다. Service의 책임만 검증하기 위해서입니다.

## 2. Service 로그를 테스트한 이유

DB 상태에 따라 로그 레벨이 달라집니다.

```text
UP   → INFO
DOWN → WARN
```

운영 시 중요한 동작이므로 `ListAppender`로 검증했습니다.

로그 전체 문자열을 비교하면 작은 문구 변경에도 테스트가 깨질 수 있으므로 핵심 필드 포함 여부만 확인했습니다.

## 3. MockMvc로 오류 응답을 검증한 이유

전역 예외 처리는 실제 Spring MVC 흐름에서 검증하는 것이 중요합니다.

다음 내용을 확인했습니다.

- HTTP 상태코드
- `application/problem+json`
- 표준 오류 필드
- `errorCode`
- `requestId`
- Validation 오류 목록
- 지원 가능한 HTTP Method

테스트용 Controller는 운영 Controller와 분리했습니다. 테스트 Slice에서 불필요한 Bean 로딩 문제를 피하기 위해서입니다.

## 4. 통합 테스트를 일반 테스트에서 분리한 이유

실제 MySQL 테스트는 Docker, Port, 계정, 비밀번호에 의존합니다.

일반 `test` Task에 포함하면 Docker가 없는 환경에서 전체 테스트가 실패하고 실행 속도도 느려집니다.

```java
@Tag("integration")
```

```bash
./gradlew test
./gradlew integrationTest
```

## 5. 통합 테스트를 check에 연결하지 않은 이유

`integrationTest`를 `check`에 연결하면 `build` 시에도 Docker MySQL이 필요합니다.

현재는 빠른 코드 검증과 외부 인프라 검증을 분리했습니다.

향후 CI에서는 별도 Stage로 통합 테스트를 실행할 예정입니다.

## 6. 테스트 비밀번호를 하드코딩하지 않은 이유

통합 테스트는 다음 환경변수를 사용합니다.

```text
DB_TARGET_HOST
DB_TARGET_PORT
DB_TARGET_NAME
DB_TARGET_USERNAME
DB_TARGET_PASSWORD
```

실행 전에 `.env`를 적용합니다.

```bash
set -a
source .env
set +a
```

장점은 환경마다 다른 값을 사용할 수 있다는 점입니다. 단점은 환경변수를 적용하지 않으면 테스트가 실패한다는 점입니다.

## 7. 현재 테스트의 한계

현재 통합 테스트는 Docker MySQL이 이미 실행되어 있어야 합니다.

향후 개선 방향은 다음과 같습니다.

- Testcontainers 적용
- 인증 실패와 네트워크 장애 주입
- API부터 DB까지 End-to-End 테스트
- CI에서 단위 테스트와 통합 테스트 분리
