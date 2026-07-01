# 오류 처리 설계

## 1. DB 연결 오류를 분류한 이유

JDBC 연결 실패는 모두 같은 장애가 아닙니다.

- 계정 또는 비밀번호 오류
- DB 서버 미실행
- 네트워크 시간초과
- Host 이름 해석 실패
- 그 외 오류

이를 모두 `UNKNOWN_ERROR`로 처리하면 운영자가 다음 조치를 판단하기 어렵습니다.

```text
AUTHENTICATION_FAILED
CONNECTION_REFUSED
CONNECTION_TIMEOUT
UNKNOWN_HOST
UNKNOWN_ERROR
```

## 2. 오류 분류기를 Adapter에서 분리한 이유

초기에는 MySQL Adapter 안에서 연결, 쿼리 실행, 오류 분류를 모두 처리했습니다.

이 구조는 클래스 하나가 여러 책임을 가지며 오류 분류만 단위 테스트하기 어렵다는 문제가 있었습니다. 그래서 `DatabaseErrorClassifier`를 분리했습니다.

장점은 분류 규칙을 독립적으로 테스트할 수 있다는 점입니다. 단점은 클래스 수가 늘어난다는 점입니다.

오류 분류는 운영 결과의 신뢰성과 직접 연결되므로 별도 책임으로 두었습니다.

## 3. 예외 체인 전체를 확인한 이유

JDBC Driver 예외는 여러 단계로 감싸질 수 있습니다.

```text
SQLException
→ CommunicationsException
→ SocketException
→ ConnectException
```

가장 마지막 원인만 확인하면 중간의 `SocketTimeoutException` 같은 중요한 정보를 놓칠 수 있습니다.

따라서 `Throwable.getCause()`를 따라 전체 체인을 확인했습니다.

현재 우선순위는 인증 실패, DNS 오류, 시간초과, 연결 거부, SQLState 보조 판정, 알 수 없는 오류 순서입니다.

## 4. 원본 예외 메시지를 API에 노출하지 않은 이유

JDBC 예외 메시지에는 DB 계정명, 내부 Host, SQL 문장, Driver 정보가 포함될 수 있습니다.

따라서 API에는 고정된 안전한 메시지만 반환합니다.

```json
{
  "errorCode": "AUTHENTICATION_FAILED",
  "message": "Database authentication failed."
}
```

원본 예외와 Stack Trace는 내부 로그에서만 확인합니다.

## 5. RFC 9457 Problem Details를 사용한 이유

400, 404, 405, 500 오류가 서로 다른 JSON 구조를 가지면 클라이언트 처리가 복잡해집니다.

Spring의 `ProblemDetail`을 사용해 오류 응답을 통일했습니다.

```json
{
  "type": "https://db-fleetops.dev/problems/resource-not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "The requested resource could not be found.",
  "instance": "/api/v1/not-exists",
  "errorCode": "DBOPS-COMMON-40401",
  "requestId": "request-001",
  "timestamp": "2026-06-29T19:00:00+09:00"
}
```

장점은 응답 규격이 일정하다는 점입니다. 단점은 단순한 프로젝트에서는 구조가 다소 복잡해 보일 수 있다는 점입니다.

운영 플랫폼은 오류 추적과 연동이 중요하므로 초기부터 규격을 통일했습니다.

## 6. Validation 오류에 필드 목록을 포함한 이유

단순히 `400 Bad Request`만 반환하면 어떤 입력값을 수정해야 하는지 알기 어렵습니다.

```json
{
  "errorCode": "DBOPS-COMMON-40002",
  "errors": [
    {
      "field": "name",
      "message": "must not be blank"
    }
  ]
}
```

장점은 API 사용자가 잘못된 필드를 바로 확인할 수 있다는 점입니다. 단점은 Validation 메시지가 외부 계약의 일부가 될 수 있다는 점입니다.
