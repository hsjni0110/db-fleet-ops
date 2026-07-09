# DB FleetOps Console Frontend Development Guidelines

이 문서는 `db-fleetops-console` 프론트엔드를 계속 구현할 때 AI와 개발자가 먼저 참고해야 하는 작업 지침이다. 목표는 Phase 12의 운영 콘솔 MVP를 유지하면서, API 연동 코드와 화면 코드가 커지거나 중복되지 않게 관리하는 것이다.

## 1. 먼저 확인할 문서와 코드

새 기능을 구현하기 전에 아래 순서로 확인한다.

1. Phase 범위: `docs/phase/phase12.md`
2. API 명세: `docs/api/*.md`
3. 현재 API client: `db-fleetops-console/src/api/*Api.ts`
4. 현재 타입: `db-fleetops-console/src/types/*.ts`
5. 라우팅/화면 구조: `db-fleetops-console/src/router/index.ts`, `src/pages/*.vue`
6. 공통 레이아웃/스타일: `src/layouts`, `src/components`, `src/styles/global.css`

API 명세에 없는 화면 기능은 임의로 endpoint를 만들지 않는다. 먼저 placeholder, empty state, 또는 TODO 성격의 UI로 두고 백엔드 API 보완이 필요하다는 것을 화면/문서에 명확히 남긴다.

## 2. 현재 기술 스택

- Vue 3 + `<script setup lang="ts">`
- Vite
- TypeScript
- Vue Router
- Pinia 사용 가능, 단 현재는 전역 상태가 꼭 필요한 경우에만 도입
- Axios 기반 API client
- Element Plus UI component
- `@element-plus/icons-vue` 아이콘

새 라이브러리는 기능 구현에 반드시 필요할 때만 추가한다. 테이블, 폼, 탭, 다이얼로그, 태그, 알림은 우선 Element Plus로 해결한다.

## 3. 디렉토리 책임

현재 구조를 유지한다.

```text
src/
  api/          백엔드 API 호출 함수
  components/   레이아웃 또는 여러 화면에서 재사용되는 UI 조각
  layouts/      AppLayout 같은 화면 뼈대
  pages/        라우트 단위 화면
  router/       Vue Router 설정
  stores/       전역 상태가 필요한 경우
  styles/       전역 CSS와 공통 utility class
  types/        API request/response 타입
```

역할 기준은 아래와 같다.

- `pages`: 라우트 파라미터 처리, 화면 조합, 사용자 액션 연결, loading/error 상태 관리
- `api`: HTTP method, URL, request header, response data 반환만 담당
- `types`: API 명세에 대응되는 request/response/type union 정의
- `components`: 여러 페이지에서 반복되는 표시 컴포넌트 또는 한 페이지가 너무 커질 때 분리한 섹션
- `stores`: 라우트를 넘어 공유해야 하는 상태만 관리
- `styles`: 화면 전반에서 재사용하는 레이아웃/표/상태 class

## 4. API 연동 규칙

API 호출은 반드시 `src/api/http.ts`의 `http` 인스턴스를 사용한다.

```ts
import { http } from "./http";
import type { ExampleResponse } from "../types";

export async function getExample(id: number): Promise<ExampleResponse> {
  const response = await http.get<ExampleResponse>(`/api/v1/examples/${id}`);

  return response.data;
}
```

규칙:

- 페이지 컴포넌트에서 `axios`를 직접 import하지 않는다.
- API 함수는 `response.data`만 반환한다.
- URL 문자열은 API client 파일 안에 둔다.
- request/response 타입은 `src/types`에 먼저 정의한다.
- API client는 도메인별 파일에 둔다: `databaseApi.ts`, `diagnosticsApi.ts`, `operationJobApi.ts` 등.
- 새 타입 파일을 만들면 `src/types/index.ts`에서 export한다.
- 새 API 파일을 만들면 `src/api/index.ts`에서 export한다.
- idempotency가 필요한 POST는 `createIdempotencyKey`와 `Idempotency-Key` header 패턴을 재사용한다.
- 에러 메시지는 가능하면 `getApiErrorMessage`를 재사용한다. 페이지마다 같은 에러 파싱 함수를 새로 만들지 않는다.

## 5. 타입 작성 규칙

타입은 API 문서의 JSON 응답을 기준으로 작성한다.

```ts
export type OperationJobStatus =
  | "QUEUED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED";

export interface OperationJobResponse {
  jobId: number;
  status: OperationJobStatus;
  createdAt: string;
}
```

규칙:

- 상태값, 엔진 종류, 작업 종류는 string union으로 정의한다.
- 서버에서 nullable인 값은 `?: string | null`처럼 명확히 표현한다.
- 날짜/시간은 서버 응답 그대로 `string`으로 둔다.
- 화면 전용 가공 타입은 API response 타입과 섞지 않는다. 필요하면 컴포넌트 내부 computed 또는 별도 view model 타입을 둔다.
- 기존 타입 이름과 의미가 겹치는 새 타입을 만들기 전에 `src/types` 전체를 검색한다.

## 6. 페이지 구현 규칙

페이지는 라우트 단위 orchestration을 담당한다. 데이터 조회, 사용자 액션, 섹션 배치를 조합하되, 한 파일에 모든 표시 로직을 계속 쌓지 않는다.

권장 페이지 흐름:

```ts
const loading = ref(false);
const error = ref<string | null>(null);
const items = ref<ExampleResponse[]>([]);

async function loadItems() {
  loading.value = true;
  error.value = null;

  try {
    items.value = await getExamples();
  } catch (caughtError) {
    error.value = getApiErrorMessage(caughtError);
  } finally {
    loading.value = false;
  }
}

onMounted(loadItems);
```

규칙:

- `loading`, `error`, `data` 상태는 API 호출 단위로 분리한다.
- 독립적으로 실패할 수 있는 섹션은 하나의 거대한 loading/error로 묶지 않는다.
- 같은 페이지 안에서 병렬 조회가 가능한 API는 `Promise.all`을 사용한다.
- 라우트 파라미터는 `computed`로 숫자 변환하여 재사용한다.
- 사용자 액션 성공/실패 피드백은 `ElMessage`를 사용한다.
- 페이지에 API endpoint 설명을 과하게 노출하지 않는다. 개발 중 placeholder 외에는 운영자가 볼 정보 위주로 구성한다.

## 7. 파일이 커질 때 분리 기준

아래 중 하나에 해당하면 페이지 파일에서 분리한다.

- `.vue` 파일이 300줄을 넘는다.
- loading/error state가 5개 이상이다.
- 테이블 column 또는 formatter가 길어진다.
- 같은 tag 색상/상태 formatter를 두 페이지 이상에서 쓴다.
- 탭 하나가 독립적으로 조회/새로고침/에러 표시를 가진다.
- 폼 submit 로직이 request 생성, 검증, confirm, API 호출을 모두 포함한다.

분리 우선순위:

1. 같은 페이지 전용 섹션 컴포넌트: `src/components`에 `DatabaseDiagnosticsPanel.vue` 같은 이름으로 추가
2. 재사용 formatter/helper: `src/utils`가 필요해질 때 추가하고, 먼저 기존 중복을 정리
3. 반복되는 API loading 패턴: composable이 필요해질 때 `src/composables` 추가
4. 라우트를 넘는 상태 공유: Pinia store

새 디렉토리는 실제로 2개 이상 파일이 필요할 때 만든다. 한 번만 쓰는 추상화는 만들지 않는다.

## 8. 컴포넌트 분리 규칙

컴포넌트는 표시 책임을 작게 가진다.

좋은 분리 예:

- `DatabaseOverviewCard.vue`: DB 기본 정보 표시
- `DatabaseDiagnosticsPanel.vue`: version/uptime/connections 표시
- `OperationJobStatusTag.vue`: Job 상태 태그 표시
- `ErrorState.vue`: 공통 에러 표시

피해야 할 분리:

- API 호출과 라우팅을 깊은 자식 컴포넌트에 흩뿌리기
- 한 번만 쓰는 작은 `<el-tag>` wrapper 만들기
- 페이지 이름과 거의 같은 거대한 container 컴포넌트 하나 더 만들기
- props로 원본 API response 전체를 넘긴 뒤 내부에서 많은 도메인 판단을 하는 컴포넌트

기본 원칙은 page가 데이터를 가져오고, component는 받은 데이터를 표시하거나 명확한 사용자 이벤트를 emit하는 것이다.

## 9. UI와 스타일 규칙

DB FleetOps Console은 운영 도구다. 화려한 랜딩 페이지가 아니라 빠르게 상태를 읽고 조치하는 화면이어야 한다.

규칙:

- 기존 `AppLayout`, `AppSidebar`, `AppHeader`, `global.css` class를 우선 사용한다.
- 화면 루트는 보통 `<section class="page-stack">`를 사용한다.
- 반복되는 테이블은 `console-table`, `table-primary-cell` 같은 기존 class를 먼저 확인한다.
- 카드 안에 카드를 중첩하지 않는다.
- 상태는 `el-tag`를 사용하고, 색상 mapping은 기존 formatter와 맞춘다.
- 버튼에는 가능한 Element Plus icon을 함께 사용한다.
- 긴 설명 문구보다 운영자가 바로 읽을 수 있는 상태, 시간, 대상, 결과를 우선한다.
- 모바일 최적화보다 현재 콘솔의 최소 폭 기반 운영 화면을 우선하되, 텍스트가 버튼/태그를 깨지 않게 한다.

## 10. 중복 방지 체크리스트

코드를 작성하기 전에 반드시 검색한다.

```bash
rg "getApiErrorMessage|createIdempotencyKey|StatusTag|formatSeconds|formatNullable" db-fleetops-console/src
rg "OperationJobStatus|DatabaseEngine|ConfigurationDrift" db-fleetops-console/src/types
rg "/api/v1/database-instances" db-fleetops-console/src/api docs/api
```

확인할 것:

- 같은 API 함수가 이미 있는가?
- 같은 response 타입이 이미 있는가?
- 같은 상태값 formatter가 페이지 안에 이미 있는가?
- 같은 empty/error/loading UI가 다른 페이지에 있는가?
- API 문서와 실제 타입 이름이 어긋나지 않는가?

중복이 보이면 새 코드를 만들기보다 기존 코드를 옮기거나 재사용 가능한 형태로 정리한다. 단, 관련 없는 큰 리팩터링은 Phase 12 MVP 진행을 방해하지 않는 범위에서만 한다.

## 11. 라우팅과 네비게이션 규칙

새 화면을 추가할 때:

1. `src/pages`에 page component 추가
2. `src/router/index.ts`에 route 추가
3. route `meta.title`, `meta.description` 작성
4. sidebar 노출이 필요한 경우 `AppSidebar.vue` 업데이트
5. 상세 화면 이동은 `router.push` 또는 `<router-link>`를 사용

라우트 이름은 기존 스타일처럼 PascalCase를 사용한다.

```ts
{
  path: "jobs/:jobId",
  name: "OperationJobDetail",
  component: OperationJobDetailPage,
}
```

## 12. Phase 12 구현 우선순위

MVP 목적에 맞춰 아래 순서로 구현한다.

1. Inventory 목록/상세 안정화
2. Health와 Diagnostics 조회
3. Backup Job 실행과 Job 상세 추적
4. Configuration Drift 확인
5. Restore Verification 확인
6. Agent 상태 확인
7. Alert 목록, 상세, Ack, Resolve
8. 배포와 관측성 문서 연결

API가 준비되지 않은 화면은 억지 mock 구현을 하지 않는다. 실제 API가 있는 기능부터 연결하고, 없는 기능은 backend gap으로 남긴다.

## 13. 작업 완료 전 확인

프론트엔드 변경 후 가능한 한 아래를 실행한다.

```bash
npm run build
```

확인 사항:

- TypeScript build가 통과하는가?
- 새 타입이 `src/types/index.ts`에서 export되는가?
- 새 API가 `src/api/index.ts`에서 export되는가?
- 페이지가 route에 연결되는가?
- loading, empty, error 상태가 모두 보이는가?
- API 실패 시 사용자가 이해할 수 있는 메시지가 있는가?
- 불필요한 새 dependency가 추가되지 않았는가?

