# 요청 추적과 로그 설계

## 1. Request ID를 도입한 이유

하나의 요청은 Filter, Controller, Service, Adapter, Database를 거칩니다.

각 계층의 로그를 연결하기 위해 요청마다 `requestId`를 부여했습니다.

Client가 유효한 `X-Request-ID`를 보내면 재사용하고, 없거나 형식이 잘못된 경우 UUID를 생성합니다.

```http
X-Request-ID: health-check-001
```

## 2. Request ID 형식을 제한한 이유

외부 Header 값을 검증 없이 로그에 사용하면 개행문자나 특수문자를 이용한 로그 위조 가능성이 있습니다.

허용 형식은 영문, 숫자, 마침표, 밑줄, 하이픈이며 최대 100자입니다. 조건에 맞지 않으면 새 UUID를 생성합니다.

## 3. MDC를 사용한 이유

모든 메서드에 `requestId`를 인자로 전달하지 않고 MDC에 저장해 로그 패턴에서 자동 출력하도록 했습니다.

```text
[requestId=health-check-001]
```

장점은 동일 Thread의 로그에 자동으로 포함된다는 점입니다. 단점은 비동기 처리에서는 MDC가 자동 전파되지 않을 수 있다는 점입니다.

현재 Health API는 동기 방식입니다. 비동기 Job 구현 시 MDC 전파를 별도로 처리할 예정입니다.

## 4. Filter 순서를 명시한 이유

요청 완료 로그가 남을 때 이미 MDC에 Request ID가 있어야 합니다.

```text
RequestIdFilter
→ RequestLoggingFilter
→ Controller
```

Filter 순서를 명시하지 않으면 요청 완료 로그에 Request ID가 빠질 수 있습니다.

## 5. 요청 완료 로그만 남긴 이유

요청 시작과 완료 로그를 모두 남기면 정상 요청마다 로그가 두 줄씩 발생합니다.

현재는 완료 로그 한 줄에 필요한 정보를 모았습니다.

```text
http_request_completed method=GET path=/api/v1/databases/default/health status=200 durationMs=18
```

장시간 실행 Job이 추가되면 시작 로그를 별도로 검토할 예정입니다.

## 6. 로그 레벨 기준

HTTP 요청 로그는 다음 기준을 사용합니다.

- 2xx, 3xx: INFO
- 4xx: WARN
- 5xx: ERROR

DB Health 로그는 다음 기준을 사용합니다.

- DB `UP`: INFO
- DB `DOWN`: WARN
- 처리되지 않은 서버 예외: ERROR

DB `DOWN`은 플랫폼이 정상적으로 감지한 운영 상태이므로 ERROR가 아니라 WARN으로 기록했습니다.

## 7. 민감정보를 로그에서 제외한 이유

Query String에는 Token, Password, Secret이 들어올 수 있습니다.

따라서 요청 로그에는 Method, Path, Status, Duration, Request ID만 기록합니다.

DB 로그에도 비밀번호, 전체 JDBC URL, Credential 값은 기록하지 않습니다.

## 8. 현재 로그의 한계

현재 로그는 Key-Value 문자열입니다.

향후 Loki, Elasticsearch, OpenSearch 연동 시 JSON Encoder 적용을 검토할 예정입니다.
