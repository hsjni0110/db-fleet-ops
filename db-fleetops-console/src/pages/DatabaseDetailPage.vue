<script setup lang="ts">
import { operationalLabel } from "../labels";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  Back,
  CircleCheckFilled,
  Refresh,
} from "@element-plus/icons-vue";

import {
  getConnectionSummary,
  connectBusinessVerificationProfile,
  getBusinessVerificationProfiles,
  getDatabaseInstance,
  getDatabaseSessions,
  getDatabaseUptime,
  getDatabaseVersion,
  getLatestConfigurationDrift,
  getLatestRestoreVerificationByDatabaseId,
  getLockWaits,
  getLongTransactions,
  getSlowQueries,
  runDatabaseHealthCheck,
} from "../api";

import type {
  ConfigurationDriftResponse,
  ConnectionSummaryResponse,
  DatabaseInstanceResponse,
  DatabaseSessionResponse,
  DatabaseUptimeResponse,
  DatabaseVersionResponse,
  InventoryHealthCheckResponse,
  LockWaitResponse,
  LongTransactionResponse,
  RestoreVerificationResponse,
  SlowQueryResponse,
  BusinessVerificationProfileResponse,
} from "../types";

const route = useRoute();
const router = useRouter();

const databaseId = computed(() => Number(route.params.databaseId));

const database = ref<DatabaseInstanceResponse | null>(null);
const health = ref<InventoryHealthCheckResponse | null>(null);
const version = ref<DatabaseVersionResponse | null>(null);
const uptime = ref<DatabaseUptimeResponse | null>(null);
const connections = ref<ConnectionSummaryResponse | null>(null);
const sessions = ref<DatabaseSessionResponse[]>([]);
const longTransactions = ref<LongTransactionResponse[]>([]);
const lockWaits = ref<LockWaitResponse[]>([]);
const slowQueries = ref<SlowQueryResponse[]>([]);
const latestDrift = ref<ConfigurationDriftResponse | null>(null);
const latestRestoreVerification = ref<RestoreVerificationResponse | null>(null);
const businessProfiles = ref<BusinessVerificationProfileResponse[]>([]);
const selectedBusinessProfileKey = ref("");
const savingBusinessProfile = ref(false);

const loadingOverview = ref(false);
const loadingHealth = ref(false);
const loadingDiagnostics = ref(false);
const loadingSessions = ref(false);
const loadingLongTransactions = ref(false);
const loadingLockWaits = ref(false);
const loadingSlowQueries = ref(false);
const loadingDrift = ref(false);
const loadingRestoreVerification = ref(false);

const overviewError = ref<string | null>(null);
const diagnosticsError = ref<string | null>(null);
const sessionsError = ref<string | null>(null);
const longTransactionsError = ref<string | null>(null);
const lockWaitsError = ref<string | null>(null);
const slowQueriesError = ref<string | null>(null);
const driftError = ref<string | null>(null);
const restoreVerificationError = ref<string | null>(null);

function getErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message;
  }

  return "알 수 없는 오류";
}

function getEngineTagType(engine?: string) {
  if (engine === "MYSQL") {
    return "primary";
  }

  if (engine === "POSTGRESQL") {
    return "success";
  }

  return "info";
}

function getInventoryStatusTagType(status?: string) {
  if (status === "ACTIVE") {
    return "success";
  }

  if (status === "INACTIVE") {
    return "info";
  }

  return "warning";
}

function getHealthTagType(status?: string) {
  if (!status) {
    return "info";
  }

  if (status === "HEALTHY" || status === "UP" || status === "VERIFIED" || status === "COMPLIANT" || status === "COMPLETED") {
    return "success";
  }

  if (status === "DEGRADED" || status === "CLEANUP_FAILED" || status === "NON_COMPLIANT") {
    return "warning";
  }

  if (status === "CRITICAL" || status === "DOWN" || status === "FAILED" || status === "ERROR" || status === "MISSING") {
    return "danger";
  }

  return "info";
}

function formatSeconds(seconds?: number | null) {
  if (seconds == null) {
    return "-";
  }

  if (seconds < 60) {
    return `${seconds}s`;
  }

  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;

  if (minutes < 60) {
    return `${minutes}m ${remainingSeconds}s`;
  }

  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;

  return `${hours}h ${remainingMinutes}m`;
}

function formatNullable(value: unknown) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return String(value);
}

function getUnsupportedMessage(featureName: string) {
  return `현재 데이터베이스에서 ${featureName} 미지원`;
}

async function loadOverview() {
  loadingOverview.value = true;
  overviewError.value = null;

  try {
    database.value = await getDatabaseInstance(databaseId.value);
    selectedBusinessProfileKey.value = database.value.businessVerificationProfileKey ?? "";
  } catch (error) {
    overviewError.value = getErrorMessage(error);
    ElMessage.error("DB 상세 정보를 불러오지 못했습니다.");
  } finally {
    loadingOverview.value = false;
  }
}

async function loadBusinessProfiles() {
  try {
    businessProfiles.value = await getBusinessVerificationProfiles();
  } catch (error) {
    ElMessage.error(`업무 검증 기준을 불러오지 못했습니다. ${getErrorMessage(error)}`);
  }
}

async function saveBusinessProfile() {
  savingBusinessProfile.value = true;
  try {
    database.value = await connectBusinessVerificationProfile(
      databaseId.value,
      selectedBusinessProfileKey.value || null,
    );
    ElMessage.success("업무 검증 기준 연결을 변경했습니다.");
  } catch (error) {
    ElMessage.error(`업무 검증 기준 변경에 실패했습니다. ${getErrorMessage(error)}`);
  } finally {
    savingBusinessProfile.value = false;
  }
}

async function runHealthCheck() {
  loadingHealth.value = true;

  try {
    health.value = await runDatabaseHealthCheck(databaseId.value);

    ElMessage.success(`연결 점검 완료 · ${health.value.status}`);
  } catch (error) {
    ElMessage.error(`연결 점검 실패. ${getErrorMessage(error)}`);
  } finally {
    loadingHealth.value = false;
  }
}

async function loadBasicDiagnostics() {
  loadingDiagnostics.value = true;
  diagnosticsError.value = null;

  try {
    const [
      versionResponse,
      uptimeResponse,
      connectionResponse,
    ] = await Promise.all([
      getDatabaseVersion(databaseId.value),
      getDatabaseUptime(databaseId.value),
      getConnectionSummary(databaseId.value),
    ]);

    version.value = versionResponse;
    uptime.value = uptimeResponse;
    connections.value = connectionResponse;
  } catch (error) {
    diagnosticsError.value = getErrorMessage(error);
  } finally {
    loadingDiagnostics.value = false;
  }
}

async function loadSessions() {
  loadingSessions.value = true;
  sessionsError.value = null;

  try {
    sessions.value = await getDatabaseSessions(databaseId.value);
  } catch (error) {
    sessionsError.value = getErrorMessage(error);
  } finally {
    loadingSessions.value = false;
  }
}

async function loadLongTransactions() {
  loadingLongTransactions.value = true;
  longTransactionsError.value = null;

  try {
    longTransactions.value = await getLongTransactions(databaseId.value);
  } catch (error) {
    longTransactionsError.value = getErrorMessage(error);
  } finally {
    loadingLongTransactions.value = false;
  }
}

async function loadLockWaits() {
  loadingLockWaits.value = true;
  lockWaitsError.value = null;

  try {
    lockWaits.value = await getLockWaits(databaseId.value);
  } catch (error) {
    lockWaitsError.value = getErrorMessage(error);
  } finally {
    loadingLockWaits.value = false;
  }
}

async function loadSlowQueries() {
  loadingSlowQueries.value = true;
  slowQueriesError.value = null;

  try {
    slowQueries.value = await getSlowQueries(databaseId.value);
  } catch (error) {
    slowQueriesError.value = getErrorMessage(error);
  } finally {
    loadingSlowQueries.value = false;
  }
}

async function loadLatestDrift() {
  loadingDrift.value = true;
  driftError.value = null;

  try {
    latestDrift.value = await getLatestConfigurationDrift(databaseId.value);
  } catch (error) {
    driftError.value = getErrorMessage(error);
  } finally {
    loadingDrift.value = false;
  }
}

async function loadLatestRestoreVerification() {
  loadingRestoreVerification.value = true;
  restoreVerificationError.value = null;

  try {
    latestRestoreVerification.value =
      await getLatestRestoreVerificationByDatabaseId(databaseId.value);
  } catch (error) {
    restoreVerificationError.value = getErrorMessage(error);
  } finally {
    loadingRestoreVerification.value = false;
  }
}

async function refreshAll() {
  await Promise.allSettled([
    loadOverview(),
    loadBasicDiagnostics(),
    loadSessions(),
    loadLongTransactions(),
    loadLockWaits(),
    loadSlowQueries(),
    loadLatestDrift(),
    loadLatestRestoreVerification(),
    loadBusinessProfiles(),
  ]);
}

function goBack() {
  router.push("/databases");
}

onMounted(() => {
  refreshAll();
});
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>데이터베이스 상세</span>
          </div>

          <div class="card-actions">
            <el-button :icon="Back" @click="goBack">
              목록
            </el-button>

            <el-button :icon="Refresh" @click="refreshAll">
              새로고침
            </el-button>

            <el-button
              type="success"
              :icon="CircleCheckFilled"
              :loading="loadingHealth"
              @click="runHealthCheck"
            >
              연결 점검
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="overviewError"
        type="error"
        show-icon
        :closable="false"
        class="page-alert"
      >
        <template #title>데이터베이스 상세 조회 실패</template>
        <p>{{ overviewError }}</p>
      </el-alert>

      <div v-loading="loadingOverview" class="detail-summary-grid">
        <div class="summary-tile">
          <span class="summary-label">데이터베이스 번호</span>
          <strong>{{ databaseId }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">이름</span>
          <strong>{{ database?.name ?? "-" }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">엔진</span>
          <el-tag :type="getEngineTagType(database?.engine)" effect="light">
            {{ operationalLabel(database?.engine ?? "-") }}
          </el-tag>
        </div>

        <div class="summary-tile">
          <span class="summary-label">등록 상태</span>
          <el-tag :type="getInventoryStatusTagType(database?.status)" effect="light">
            {{ operationalLabel(database?.status ?? "-") }}
          </el-tag>
        </div>

        <div class="summary-tile">
          <span class="summary-label">호스트</span>
          <strong>{{ database?.host ?? "-" }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">포트</span>
          <strong>{{ database?.port ?? "-" }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">데이터베이스 이름</span>
          <strong>{{ database?.databaseName ?? "-" }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">환경</span>
          <strong>{{ operationalLabel(database?.environment ?? "-") }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">업무 검증 기준</span>
          <strong>{{ database?.businessVerificationProfileKey ?? "미지정" }}</strong>
        </div>
      </div>
    </el-card>

    <el-tabs type="border-card" class="detail-tabs">
      <el-tab-pane label="기본 정보">
        <div class="tab-toolbar">
          <h3>기본 정보</h3>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="서비스 이름">
            {{ formatNullable(database?.serviceName) }}
          </el-descriptions-item>

          <el-descriptions-item label="담당자">
            {{ formatNullable(database?.owner) }}
          </el-descriptions-item>

          <el-descriptions-item label="설명" :span="2">
            {{ formatNullable(database?.description) }}
          </el-descriptions-item>

          <el-descriptions-item label="업무 검증 기준" :span="2">
            <div class="card-actions">
              <el-select v-model="selectedBusinessProfileKey" :empty-values="[null, undefined]" class="full-width">
                <el-option label="기본 복원 검증만 적용" value="" />
                <el-option
                  v-for="profile in businessProfiles"
                  :key="profile.key"
                  :label="`${profile.name} (규칙 ${profile.ruleCount}개)`"
                  :value="profile.key"
                  :disabled="profile.engine !== database?.engine"
                />
              </el-select>
              <el-button type="primary" :loading="savingBusinessProfile" @click="saveBusinessProfile">
                검증 기준 저장
              </el-button>
            </div>
          </el-descriptions-item>

          <el-descriptions-item label="최근 연결 상태">
            <el-tag :type="getHealthTagType(health?.status)" effect="light">
              {{ operationalLabel(health?.status ?? "미점검") }}
            </el-tag>
          </el-descriptions-item>

          <el-descriptions-item label="응답 시간">
            {{ health?.responseTimeMs ?? "-" }}ms
          </el-descriptions-item>

          <el-descriptions-item label="점검 메시지" :span="2">
            {{ health?.message ?? "연결 점검 필요" }}
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <el-tab-pane label="연결·서버 정보">
        <div class="tab-toolbar">
          <h3>연결 및 서버 진단</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingDiagnostics"
            @click="loadBasicDiagnostics"
          >
            새로고침
          </el-button>
        </div>

        <el-alert
          v-if="diagnosticsError"
          type="error"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>서버 진단 조회 실패</template>
          <p>{{ diagnosticsError }}</p>
        </el-alert>

        <div v-loading="loadingDiagnostics" class="metric-grid">
          <div class="metric-card">
            <span class="metric-label">버전</span>
            <strong>{{ version?.version ?? "-" }}</strong>
            <small>{{ operationalLabel(version?.engine ?? database?.engine) }}</small>
          </div>

          <div class="metric-card">
            <span class="metric-label">가동 시간</span>
            <strong>{{ formatSeconds(uptime?.uptimeSeconds) }}</strong>
            <small>{{ uptime?.uptimeSeconds ?? "-" }} 초</small>
          </div>

          <div class="metric-card">
            <span class="metric-label">현재 연결 수</span>
            <strong>{{ connections?.currentConnections ?? "-" }}</strong>
            <small>실행 중 {{ connections?.runningConnections ?? "—" }}개</small>
          </div>

          <div class="metric-card">
            <span class="metric-label">연결 사용률</span>
            <strong>{{ connections?.usagePercent ?? "-" }}%</strong>
            <small>최대 {{ connections?.maxConnections ?? "-" }}</small>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="세션">
        <div class="tab-toolbar">
          <h3>세션</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingSessions"
            @click="loadSessions"
          >
            새로고침
          </el-button>
        </div>

        <el-alert
          v-if="sessionsError"
          type="error"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>세션 조회 실패</template>
          <p>{{ sessionsError }}</p>
        </el-alert>

        <el-table scrollbar-tabindex="0"
          v-loading="loadingSessions"
          :data="sessions"
          border
          stripe
          empty-text="조회된 세션 없음"
        >
          <el-table-column prop="processId" label="프로세스 번호" width="120" />
          <el-table-column prop="user" label="사용자" width="150" />
          <el-table-column prop="host" label="호스트" min-width="180" />
          <el-table-column prop="databaseName" label="데이터베이스" width="150" />
          <el-table-column prop="command" label="명령" width="130" />
          <el-table-column prop="timeSeconds" label="경과 시간" width="110">
            <template #default="{ row }">
              {{ formatSeconds(row.timeSeconds) }}
            </template>
          </el-table-column>
          <el-table-column prop="state" label="상태" min-width="180" />
          <el-table-column prop="queryPreview" label="쿼리" min-width="320" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="장기 실행 트랜잭션">
        <div class="tab-toolbar">
          <h3>장기 실행 트랜잭션</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingLongTransactions"
            @click="loadLongTransactions"
          >
            새로고침
          </el-button>
        </div>

        <el-alert
          v-if="longTransactionsError"
          type="error"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>장기 실행 트랜잭션 조회 실패</template>
          <p>{{ longTransactionsError }}</p>
        </el-alert>

        <el-table scrollbar-tabindex="0"
          v-loading="loadingLongTransactions"
          :data="longTransactions"
          border
          stripe
          empty-text="장기 실행 트랜잭션 없음"
        >
          <el-table-column prop="transactionId" label="트랜잭션 번호" min-width="160" />
          <el-table-column prop="state" label="상태" width="140" />
          <el-table-column prop="startedAt" label="시작 시각" min-width="180" />
          <el-table-column prop="durationSeconds" label="실행 시간" width="140">
            <template #default="{ row }">
              {{ formatSeconds(row.durationSeconds) }}
            </template>
          </el-table-column>
          <el-table-column prop="threadId" label="스레드 번호" width="120" />
          <el-table-column prop="queryPreview" label="쿼리" min-width="320" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="잠금 대기">
        <div class="tab-toolbar">
          <h3>잠금 대기</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingLockWaits"
            @click="loadLockWaits"
          >
            새로고침
          </el-button>
        </div>

        <el-alert
          v-if="lockWaitsError"
          type="error"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>잠금 대기 조회 실패</template>
          <p>{{ lockWaitsError }}</p>
        </el-alert>

        <el-table scrollbar-tabindex="0"
          v-loading="loadingLockWaits"
          :data="lockWaits"
          border
          stripe
          empty-text="잠금 대기 없음"
        >
          <el-table-column prop="waitingThreadId" label="대기 세션" width="110" />
          <el-table-column prop="blockingThreadId" label="잠금 보유 세션" width="125" />
          <el-table-column prop="waitingTransactionId" label="대기 트랜잭션" min-width="130" />
          <el-table-column prop="blockingTransactionId" label="잠금 보유 트랜잭션" min-width="145" />
          <el-table-column prop="waitingQueryPreview" label="대기 쿼리" min-width="300" show-overflow-tooltip />
          <el-table-column prop="blockingQueryPreview" label="잠금 보유 쿼리" min-width="300" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="느린 쿼리">
        <div class="tab-toolbar">
          <h3>느린 쿼리 후보</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingSlowQueries"
            @click="loadSlowQueries"
          >
            새로고침
          </el-button>
        </div>

        <el-alert
          v-if="slowQueriesError"
          type="warning"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>느린 쿼리 조회 불가</template>
          <p>{{ slowQueriesError }}</p>
          <p class="muted-text">
            {{ getUnsupportedMessage("느린 쿼리 분석") }}
          </p>
        </el-alert>

        <el-table scrollbar-tabindex="0"
          v-loading="loadingSlowQueries"
          :data="slowQueries"
          border
          stripe
          empty-text="느린 쿼리 후보 없음"
        >
          <el-table-column prop="digestText" label="쿼리 패턴" min-width="360" show-overflow-tooltip />
          <el-table-column prop="executionCount" label="실행 횟수" width="160" />
          <el-table-column prop="averageSeconds" label="평균 시간 (초)" width="140" />
          <el-table-column prop="maxSeconds" label="최대 시간 (초)" width="140" />
          <el-table-column prop="rowsExamined" label="검사한 행" width="160" />
          <el-table-column prop="rowsSent" label="반환한 행" width="130" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="설정 차이">
        <div class="tab-toolbar">
          <h3>최근 설정 점검</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingDrift"
            @click="loadLatestDrift"
          >
            새로고침
          </el-button>
        </div>

        <el-alert
          v-if="driftError"
          type="warning"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>최근 설정 점검 조회 불가</template>
          <p>{{ driftError }}</p>
        </el-alert>

        <div v-if="latestDrift" v-loading="loadingDrift" class="section-stack">
          <div class="metric-grid">
            <div class="metric-card">
              <span class="metric-label">상태</span>
              <el-tag :type="getHealthTagType(latestDrift.status)" effect="dark">
                {{ operationalLabel(latestDrift.status) }}
              </el-tag>
              <small>점검 #{{ latestDrift.driftId }}</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">점검 항목</span>
              <strong>{{ latestDrift.totalCount }}</strong>
              <small>점검 항목</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">기준 일치</span>
              <strong>{{ latestDrift.compliantCount }}</strong>

            </div>

            <div class="metric-card">
              <span class="metric-label">불일치 / 누락</span>
              <strong>{{ latestDrift.nonCompliantCount }} / {{ latestDrift.missingCount }}</strong>
              <small>{{ latestDrift.checkedAt }}</small>
            </div>
          </div>

          <el-table scrollbar-tabindex="0"
            :data="latestDrift.items"
            border
            stripe
            empty-text="항목별 결과 없음"
          >
            <el-table-column prop="parameterName" label="설정 항목" min-width="180" />
            <el-table-column prop="expectedValue" label="기준값" min-width="140" />
            <el-table-column prop="actualValue" label="현재값" min-width="140" />
            <el-table-column prop="valueType" label="유형" width="120">
              <template #default="{ row }">{{ operationalLabel(row.valueType) }}</template>
            </el-table-column>
            <el-table-column prop="dynamic" label="재시작 없이 변경" width="100" />
            <el-table-column prop="applyAllowed" label="변경 허용" width="130" />
            <el-table-column prop="complianceStatus" label="상태" width="160">
              <template #default="{ row }">
                <el-tag :type="getHealthTagType(row.complianceStatus)" effect="light">
                  {{ operationalLabel(row.complianceStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="메시지" min-width="300" show-overflow-tooltip />
          </el-table>
        </div>

        <el-empty
          v-else-if="!loadingDrift && !driftError"
          description="설정 점검 결과 없음"
        />
      </el-tab-pane>

      <el-tab-pane label="복원 검증">
        <div class="tab-toolbar">
          <h3>최근 복원 검증</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingRestoreVerification"
            @click="loadLatestRestoreVerification"
          >
            새로고침
          </el-button>
        </div>

        <el-alert
          v-if="restoreVerificationError"
          type="warning"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>복원 검증 결과 조회 불가</template>
          <p>{{ restoreVerificationError }}</p>
        </el-alert>

        <div
          v-if="latestRestoreVerification"
          v-loading="loadingRestoreVerification"
          class="section-stack"
        >
          <div class="metric-grid">
            <div class="metric-card">
              <span class="metric-label">상태</span>
              <el-tag
                :type="getHealthTagType(latestRestoreVerification.status)"
                effect="dark"
              >
                {{ operationalLabel(latestRestoreVerification.status) }}
              </el-tag>
              <small>검증 #{{ latestRestoreVerification.id }}</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">원본 데이터베이스</span>
              <strong>{{ latestRestoreVerification.sourceDatabaseName }}</strong>
              <small>{{ latestRestoreVerification.temporaryDatabaseName }}</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">검증 / 복원 테이블</span>
              <strong>
                {{ latestRestoreVerification.checkedTableCount }} /
                {{ latestRestoreVerification.restoredTableCount }}
              </strong>

            </div>

            <div class="metric-card">
              <span class="metric-label">검증 행 수</span>
              <strong>{{ latestRestoreVerification.totalRowCount }}</strong>
              <small>{{ latestRestoreVerification.completedAt ?? "-" }}</small>
            </div>

            <div v-if="latestRestoreVerification.technicalStatus" class="metric-card">
              <span class="metric-label">기본 복원 검증</span>
              <el-tag :type="getHealthTagType(latestRestoreVerification.technicalStatus)" effect="light">
                {{ operationalLabel(latestRestoreVerification.technicalStatus) }}
              </el-tag>
            </div>

            <div v-if="latestRestoreVerification.businessVerification" class="metric-card">
              <span class="metric-label">업무 검증</span>
              <el-tag :type="getHealthTagType(latestRestoreVerification.businessVerification.status)" effect="light">
                {{ operationalLabel(latestRestoreVerification.businessVerification.status) }}
              </el-tag>
              <small>{{ latestRestoreVerification.businessVerification.profileKey ?? "기준 없음" }}</small>
            </div>

            <div v-if="latestRestoreVerification.cleanupStatus" class="metric-card">
              <span class="metric-label">임시 데이터베이스 정리</span>
              <el-tag :type="getHealthTagType(latestRestoreVerification.cleanupStatus)" effect="light">
                {{ operationalLabel(latestRestoreVerification.cleanupStatus) }}
              </el-tag>
            </div>
          </div>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="백업 파일">
              {{ latestRestoreVerification.backupFile }}
            </el-descriptions-item>

            <el-descriptions-item label="오류">
              {{ latestRestoreVerification.errorCode ?? "-" }}
              {{ latestRestoreVerification.errorMessage ?? "" }}
            </el-descriptions-item>
          </el-descriptions>

          <el-table scrollbar-tabindex="0"
            :data="latestRestoreVerification.items"
            border
            stripe
            empty-text="테이블별 결과 없음"
          >
            <el-table-column prop="tableName" label="테이블" min-width="180" />
            <el-table-column prop="existsInRestoredDb" label="존재 여부" width="110">
              <template #default="{ row }">{{ row.existsInRestoredDb ? "있음" : "없음" }}</template>
            </el-table-column>
            <el-table-column prop="rowCount" label="행 수" width="130" />
            <el-table-column prop="status" label="상태" width="150">
              <template #default="{ row }">
                <el-tag :type="getHealthTagType(row.status)" effect="light">
                  {{ operationalLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="메시지" min-width="300" show-overflow-tooltip />
          </el-table>

          <h3 v-if="latestRestoreVerification.businessVerification">업무 규칙 검증 결과</h3>
          <el-table scrollbar-tabindex="0"
            v-if="latestRestoreVerification.businessVerification"
            :data="latestRestoreVerification.businessVerification.ruleResults"
            border
            stripe
            empty-text="업무 규칙 결과 없음"
          >
            <el-table-column prop="ruleCode" label="규칙 코드" min-width="250" />
            <el-table-column prop="ruleName" label="이름" min-width="200" />
            <el-table-column prop="status" label="상태" width="120">
              <template #default="{ row }">
                <el-tag :type="getHealthTagType(row.status)" effect="light">{{ operationalLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="violationCount" label="위반 건수" width="120" />
            <el-table-column prop="durationMillis" label="실행 시간 (ms)" width="140" />
            <el-table-column prop="message" label="메시지" min-width="260" show-overflow-tooltip />
          </el-table>
        </div>

        <el-empty
          v-else-if="!loadingRestoreVerification && !restoreVerificationError"
          description="복원 검증 결과 없음"
        />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
