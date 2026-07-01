좋음. Phase 3는 이렇게 잡는 게 좋음.

Phase 3 목표
= 등록된 MySQL 인스턴스에 대해 운영 진단 데이터를 조회하는 기능

핵심은 “임의 SQL 실행 API”를 만들지 않는 것임.

⸻

1. Phase 3 구현 범위

이번 Phase에서 구현할 기능은 아래로 제한하는 게 좋음.

1. DB 버전 조회
2. DB Uptime 조회
3. Connection 현황 조회
4. Connection 사용률 계산
5. Active / Sleep 세션 조회
6. 장기 실행 Transaction 조회
7. Lock Wait 조회
8. Slow Query 후보 조회

처음부터 저장까지 하지 말고, Phase 3-1에서는 조회 API 중심으로 가는 게 좋음.

Phase 3-1: 실시간 진단 조회
Phase 3-2: 진단 결과 저장
Phase 3-3: Health Score 반영

⸻

2. 디렉토리 전략

기존 구조를 깨지 않고 health 모듈 아래에 진단 기능을 추가함.

src/main/java/com/dbfleetops/health
├── api
│   └── DatabaseDiagnosticController.java
│
├── application
│   ├── DatabaseDiagnosticService.java
│   ├── DatabaseDiagnosticAdapter.java
│   └── DatabaseDiagnosticAdapterFactory.java
│
├── domain
│   ├── DatabaseVersionInfo.java
│   ├── DatabaseUptimeInfo.java
│   ├── ConnectionSummary.java
│   ├── SessionInfo.java
│   ├── LongTransactionInfo.java
│   ├── LockWaitInfo.java
│   └── SlowQueryInfo.java
│
├── dto
│   ├── DatabaseVersionResponse.java
│   ├── DatabaseUptimeResponse.java
│   ├── ConnectionSummaryResponse.java
│   ├── SessionResponse.java
│   ├── LongTransactionResponse.java
│   ├── LockWaitResponse.java
│   └── SlowQueryResponse.java
│
└── infra
    └── MySqlDiagnosticAdapter.java

이유:

database 모듈
→ 인벤토리 관리 책임
health 모듈
→ 상태 점검과 진단 책임
infra
→ MySQL 실제 SQL 실행 책임

⸻

3. API 설계

Phase 3 API는 전부 조회성 GET으로 잡음.

GET /api/v1/database-instances/{databaseId}/diagnostics/version
GET /api/v1/database-instances/{databaseId}/diagnostics/uptime
GET /api/v1/database-instances/{databaseId}/diagnostics/connections
GET /api/v1/database-instances/{databaseId}/diagnostics/sessions
GET /api/v1/database-instances/{databaseId}/diagnostics/long-transactions
GET /api/v1/database-instances/{databaseId}/diagnostics/lock-waits
GET /api/v1/database-instances/{databaseId}/diagnostics/slow-queries

절대 만들지 말아야 할 API:

POST /api/v1/database-instances/{id}/query
POST /api/v1/database-instances/{id}/sql

이유:

- SQL Injection 위험
- 권한 통제 어려움
- 플랫폼이 DB 원격 터미널로 변질됨
- 운영 자동화 플랫폼이라는 목적과 어긋남

⸻

4. Adapter 인터페이스 설계

package com.dbfleetops.health.application;
import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.health.domain.*;
import java.util.List;
public interface DatabaseDiagnosticAdapter {
    DatabaseEngine supports();
    DatabaseVersionInfo getVersion(
            ManagedDatabase database,
            DatabaseCredential credential
    );
    DatabaseUptimeInfo getUptime(
            ManagedDatabase database,
            DatabaseCredential credential
    );
    ConnectionSummary getConnectionSummary(
            ManagedDatabase database,
            DatabaseCredential credential
    );
    List<SessionInfo> getSessions(
            ManagedDatabase database,
            DatabaseCredential credential
    );
    List<LongTransactionInfo> getLongTransactions(
            ManagedDatabase database,
            DatabaseCredential credential
    );
    List<LockWaitInfo> getLockWaits(
            ManagedDatabase database,
            DatabaseCredential credential
    );
    List<SlowQueryInfo> getSlowQueries(
            ManagedDatabase database,
            DatabaseCredential credential
    );
}

⸻

5. Service 흐름

모든 진단 API는 같은 흐름을 사용함.

databaseId 수신
  ↓
ManagedDatabase 조회
  ↓
ACTIVE 상태 확인
  ↓
Credential 조회
  ↓
engine 기준 DiagnosticAdapter 선택
  ↓
Adapter 메서드 호출
  ↓
DTO 변환
  ↓
응답 반환

DatabaseDiagnosticService는 공통 조회 로직을 재사용해야 함.

private DiagnosticTarget getTarget(Long databaseId) {
    ManagedDatabase database = databaseRepository.findById(databaseId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Database not found. databaseId=" + databaseId
            ));
    if (!database.isActive()) {
        throw new IllegalStateException(
                "Inactive database cannot be diagnosed. databaseId=" + databaseId
        );
    }
    DatabaseCredential credential = credentialRepository.findByDatabaseId(databaseId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Credential not found. databaseId=" + databaseId
            ));
    DatabaseDiagnosticAdapter adapter =
            adapterFactory.getAdapter(database.getEngine());
    return new DiagnosticTarget(database, credential, adapter);
}

⸻

6. MySQL 진단 SQL 설계

6.1 Version

SELECT VERSION() AS version;

응답:

{
  "databaseId": 1,
  "engine": "MYSQL",
  "version": "8.4.0"
}

⸻

6.2 Uptime

SHOW GLOBAL STATUS LIKE 'Uptime';

응답:

{
  "databaseId": 1,
  "uptimeSeconds": 3600
}

⸻

6.3 Connection Summary

필요한 값:

SHOW GLOBAL STATUS LIKE 'Threads_connected';
SHOW GLOBAL STATUS LIKE 'Threads_running';
SHOW GLOBAL VARIABLES LIKE 'max_connections';

계산:

connectionUsagePercent =
Threads_connected / max_connections * 100

응답:

{
  "databaseId": 1,
  "currentConnections": 12,
  "runningConnections": 2,
  "maxConnections": 151,
  "usagePercent": 7.95
}

⸻

6.4 Sessions

SELECT
    ID,
    USER,
    HOST,
    DB,
    COMMAND,
    TIME,
    STATE,
    INFO
FROM information_schema.PROCESSLIST
ORDER BY TIME DESC;

주의:

INFO는 SQL 전체를 노출하지 않는 게 좋음.
최대 300자 정도로 자르기.

⸻

6.5 Long Transactions

SELECT
    trx_id,
    trx_state,
    trx_started,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS duration_seconds,
    trx_mysql_thread_id,
    trx_query
FROM information_schema.innodb_trx
ORDER BY trx_started ASC;

기준:

durationSeconds >= 30초 이상이면 장기 실행 Transaction 후보

이 값은 나중에 설정값으로 빼면 됨.

⸻

6.6 Lock Wait

가능하면 MySQL 8 기준으로 아래를 우선 사용함.

SELECT
    waiting_trx.trx_id AS waiting_trx_id,
    waiting_trx.trx_mysql_thread_id AS waiting_thread_id,
    waiting_trx.trx_query AS waiting_query,
    blocking_trx.trx_id AS blocking_trx_id,
    blocking_trx.trx_mysql_thread_id AS blocking_thread_id,
    blocking_trx.trx_query AS blocking_query
FROM information_schema.innodb_lock_waits lock_waits
JOIN information_schema.innodb_trx waiting_trx
    ON lock_waits.requesting_trx_id = waiting_trx.trx_id
JOIN information_schema.innodb_trx blocking_trx
    ON lock_waits.blocking_trx_id = blocking_trx.trx_id;

주의:

환경에 따라 innodb_lock_waits 권한 또는 테이블 접근이 제한될 수 있음.
이 경우 빈 목록 또는 명확한 진단 실패 응답을 고민해야 함.

초기 구현은 예외를 던지고, 전역 예외 응답으로 처리해도 됨.

⸻

6.7 Slow Query 후보

운영 DB에서 Slow Query Log 파일을 직접 읽지 않음.

초기에는 Performance Schema 기반으로 후보를 조회함.

SELECT
    DIGEST_TEXT,
    COUNT_STAR,
    ROUND(AVG_TIMER_WAIT / 1000000000000, 6) AS avg_seconds,
    ROUND(MAX_TIMER_WAIT / 1000000000000, 6) AS max_seconds,
    SUM_ROWS_EXAMINED,
    SUM_ROWS_SENT
FROM performance_schema.events_statements_summary_by_digest
WHERE DIGEST_TEXT IS NOT NULL
ORDER BY AVG_TIMER_WAIT DESC
LIMIT 10;

주의:

Performance Schema가 꺼져 있으면 데이터가 없을 수 있음.
이 경우 빈 배열이 정상일 수 있음.

⸻

7. Domain 모델 설계

예시:

public record ConnectionSummary(
        int currentConnections,
        int runningConnections,
        int maxConnections,
        double usagePercent
) {
}
public record SessionInfo(
        long processId,
        String user,
        String host,
        String databaseName,
        String command,
        long timeSeconds,
        String state,
        String queryPreview
) {
}
public record SlowQueryInfo(
        String digestText,
        long executionCount,
        double averageSeconds,
        double maxSeconds,
        long rowsExamined,
        long rowsSent
) {
}

Domain에는 HTTP 용어를 넣지 않음.

⸻

8. DTO 전략

DTO에는 databaseId, engine, checkedAt 같은 API 친화 필드를 포함함.

예:

public record ConnectionSummaryResponse(
        Long databaseId,
        String engine,
        int currentConnections,
        int runningConnections,
        int maxConnections,
        double usagePercent
) {
    public static ConnectionSummaryResponse from(
            Long databaseId,
            DatabaseEngine engine,
            ConnectionSummary summary
    ) {
        return new ConnectionSummaryResponse(
                databaseId,
                engine.name(),
                summary.currentConnections(),
                summary.runningConnections(),
                summary.maxConnections(),
                summary.usagePercent()
        );
    }
}

⸻

9. Controller 전략

Controller는 얇게 유지함.

@RestController
@RequestMapping("/api/v1/database-instances/{databaseId}/diagnostics")
public class DatabaseDiagnosticController {
    private final DatabaseDiagnosticService diagnosticService;
    public DatabaseDiagnosticController(
            DatabaseDiagnosticService diagnosticService
    ) {
        this.diagnosticService = diagnosticService;
    }
    @GetMapping("/connections")
    public ResponseEntity<ConnectionSummaryResponse> getConnectionSummary(
            @PathVariable Long databaseId
    ) {
        return ResponseEntity.ok(
                diagnosticService.getConnectionSummary(databaseId)
        );
    }
}

Controller에서 Repository나 Adapter를 직접 호출하지 않음.

⸻

10. 테스트 전략

10.1 Service 단위 테스트

Mock 대상:

ManagedDatabaseRepository
DatabaseCredentialRepository
DatabaseDiagnosticAdapterFactory
DatabaseDiagnosticAdapter

검증:

- databaseId로 DB 조회
- INACTIVE DB는 진단하지 않음
- engine 기준 Adapter 선택
- Adapter 호출 결과를 DTO로 변환

⸻

10.2 Adapter 단위 테스트

MySqlDiagnosticAdapter는 JDBC를 직접 사용하므로 완전한 단위 테스트보다 통합 테스트가 더 적합함.

단, 계산 로직은 별도 클래스로 분리 가능함.

예:

ConnectionUsageCalculator

테스트:

current=50, max=100 → 50.0
max=0 → 0.0 또는 예외

⸻

10.3 Controller MVC 테스트

검증:

- URL 매핑
- HTTP Status
- JSON 응답 구조
- Service 호출 여부

⸻

10.4 Integration Test

기존 전략 유지.

src/integrationTest/java

테스트 대상:

MySqlDiagnosticAdapterIntegrationTest

검증:

- version 조회 성공
- uptime 조회 성공
- connection summary 조회 성공
- sessions 조회 성공
- 잘못된 credential이면 예외 또는 실패 확인

Lock Wait와 Slow Query는 재현 비용이 있으므로 처음부터 강제하지 않는 게 좋음.

Phase 3-1 integration
- version
- uptime
- connection
Phase 3-2 integration
- long transaction
- lock wait
- slow query

⸻

11. 구현 순서

가장 안전한 순서는 이거임.

1. Domain record 추가
2. DiagnosticAdapter 인터페이스 추가
3. DiagnosticAdapterFactory 추가
4. MySqlDiagnosticAdapter skeleton 추가
5. DiagnosticService 추가
6. DiagnosticController 추가
7. version API 구현
8. uptime API 구현
9. connection summary API 구현
10. sessions API 구현
11. long transactions API 구현
12. lock waits API 구현
13. slow queries API 구현
14. Service 테스트 추가
15. Controller 테스트 추가
16. Integration 테스트 추가
17. API 문서 업데이트
18. Architecture 문서 업데이트

한 번에 전부 하지 말고, version → uptime → connections까지 먼저 빌드 통과시키는 게 좋음.

⸻

12. Phase 3 완료 기준

Phase 3 완료 기준은 이렇게 잡으면 됨.

기능
- 등록 DB 기준 version 조회 가능
- uptime 조회 가능
- connection summary 조회 가능
- session 목록 조회 가능
- long transaction 조회 가능
- lock wait 조회 가능
- slow query 후보 조회 가능
구조
- MySQL SQL은 MySqlDiagnosticAdapter에만 존재
- Service는 DB 조회와 Adapter 선택만 담당
- Controller는 HTTP 처리만 담당
- Inventory와 Diagnostic 책임이 분리됨
테스트
- Service 단위 테스트 통과
- Controller MVC 테스트 통과
- MySQL Adapter 통합 테스트 일부 통과
- ./gradlew test 통과
- ./gradlew integrationTest 통과
보안
- Credential 응답 노출 없음
- 임의 SQL 실행 API 없음
- Query Preview는 길이 제한 적용
문서
- API 문서 업데이트
- 아키텍처 문서 업데이트
- Phase 3 진행 기록 작성

⸻

13. 커밋 단위

커밋을 잘게 나누는 게 좋음.

git commit -m "feat: add diagnostic domain and adapter interface"
git commit -m "feat: add mysql version and uptime diagnostics"
git commit -m "feat: add mysql connection diagnostics"
git commit -m "feat: add mysql session diagnostics"
git commit -m "feat: add mysql transaction and lock diagnostics"
git commit -m "feat: add mysql slow query diagnostics"
git commit -m "test: add database diagnostics tests"
git commit -m "docs: update diagnostics api and architecture"

⸻

14. 바로 다음 구현 시작점

다음 답변에서는 바로 아래부터 구현하면 됨.

1. mkdir / touch 명령어
2. Domain record 코드
3. Adapter 인터페이스 코드
4. Factory 코드
5. Service 코드
6. Controller 코드
7. MySqlDiagnosticAdapter version/uptime/connections 구현
8. 테스트 코드