# 아키텍처 설계 결정

## 1. Controller에서 JDBC를 직접 사용하지 않은 이유

Controller는 HTTP 요청과 응답 처리만 담당하도록 했습니다.

```text
Controller
→ Service
→ Port
→ Adapter
```

Controller에서 JDBC를 직접 호출하면 HTTP 처리와 DB 연결 로직이 섞입니다. 이 경우 다른 DBMS를 추가할 때 Controller까지 수정해야 하고, 단위 테스트도 실제 DB에 의존하게 됩니다.

Service와 Port를 둔 구조는 코드가 늘어나는 단점이 있습니다. 현재 기능만 보면 단순 호출을 여러 계층으로 나눈 것처럼 보일 수 있습니다. 다만 향후 Inventory 조회, Credential 조회, DBMS별 Adapter 선택, 결과 저장 기능을 고려해 이 구조를 선택했습니다.

## 2. Port와 Adapter를 분리한 이유

`DatabaseHealthProbe`는 상태 점검 규약이며, `MySqlDatabaseHealthProbe`는 MySQL 전용 구현입니다.

```text
DatabaseHealthProbe
        ↑
MySqlDatabaseHealthProbe
```

Application 계층은 MySQL JDBC 구현을 직접 알지 않고 Port에만 의존합니다.

장점은 PostgreSQL 등 다른 DBMS를 추가해도 Service 수정이 적다는 점입니다. 단점은 구현체가 하나뿐인 현재 단계에서는 추상화가 과해 보일 수 있다는 점입니다.

프로젝트 목표가 여러 DBMS를 관리하는 플랫폼이므로 초기부터 경계를 두었습니다.

## 3. Domain과 API Response를 분리한 이유

내부 점검 결과는 `DatabaseHealth`, 외부 응답은 `DatabaseHealthResponse`로 분리했습니다.

- `DatabaseHealth`: 내부 비즈니스 결과입니다.
- `DatabaseHealthResponse`: 외부 API 계약입니다.

내부 객체를 그대로 반환하면 SQLState, Vendor Error Code 등 내부 진단정보가 외부에 노출될 가능성이 있습니다.

변환 코드가 추가되는 단점은 있지만, 내부 모델 변경이 API 변경으로 바로 이어지지 않도록 분리했습니다.

## 4. DB가 DOWN이어도 HTTP 200을 반환한 이유

관리 대상 DB 연결 실패와 DB FleetOps 서버 오류는 다른 상황입니다.

```text
관리 대상 DB 연결 실패
→ HTTP 200
→ status = DOWN

DB FleetOps 내부 오류
→ HTTP 500
```

API 서버가 상태를 정상적으로 확인했다면 요청은 성공한 것입니다.

장점은 플랫폼 장애와 대상 DB 장애를 구분할 수 있다는 점입니다. 단점은 HTTP 상태코드만 보는 모니터링에서는 DB 장애를 놓칠 수 있다는 점입니다. 따라서 클라이언트는 응답의 `status`와 `errorCode`를 함께 확인해야 합니다.

## 5. 환경변수로 접속정보를 관리한 이유

DB 비밀번호를 `application.yml`에 직접 작성하지 않았습니다.

```yaml
password: ${DB_TARGET_PASSWORD}
```

비밀번호가 Git에 포함되는 것을 막고, 환경별 설정을 코드 변경 없이 적용하기 위해서입니다.

로컬에서는 `.env`를 사용하고 Git에서 제외합니다. 변수 형식은 `.env.example`로 공유합니다.

실행 전에 환경변수를 적용해야 하는 번거로움은 있지만 운영환경과 CI로 확장하기에 더 안전합니다.

## 6. 단일 Gradle 프로젝트로 시작한 이유

향후 API, Worker, Adapter를 멀티 모듈로 나눌 수 있습니다. 하지만 초기부터 모듈을 과도하게 나누면 설정과 의존성 관리 비용이 커집니다.

현재는 하나의 Gradle 프로젝트 안에서 패키지 경계를 명확히 두었습니다.

기능과 배포 단위가 실제로 분리되는 시점에 멀티 모듈 전환을 검토할 예정입니다.
