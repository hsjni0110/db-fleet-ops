<script setup lang="ts">
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

  return "Unknown error occurred.";
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

  if (status === "HEALTHY" || status === "UP" || status === "VERIFIED" || status === "COMPLIANT") {
    return "success";
  }

  if (status === "DEGRADED" || status === "CLEANUP_FAILED" || status === "NON_COMPLIANT") {
    return "warning";
  }

  if (status === "CRITICAL" || status === "DOWN" || status === "FAILED" || status === "MISSING") {
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
  return `${featureName} is not available for this database or current backend capability.`;
}

async function loadOverview() {
  loadingOverview.value = true;
  overviewError.value = null;

  try {
    database.value = await getDatabaseInstance(databaseId.value);
  } catch (error) {
    overviewError.value = getErrorMessage(error);
    ElMessage.error("DB 상세 정보를 불러오지 못했습니다.");
  } finally {
    loadingOverview.value = false;
  }
}

async function runHealthCheck() {
  loadingHealth.value = true;

  try {
    health.value = await runDatabaseHealthCheck(databaseId.value);

    ElMessage.success(`Health Check completed: ${health.value.status}`);
  } catch (error) {
    ElMessage.error(`Health Check failed. ${getErrorMessage(error)}`);
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
            <span>Database Detail</span>
            <p class="card-subtitle">
              DB 기본 정보, Health, Diagnostics, Drift, Restore Verification을 확인합니다.
            </p>
          </div>

          <div class="card-actions">
            <el-button :icon="Back" @click="goBack">
              Back
            </el-button>

            <el-button :icon="Refresh" @click="refreshAll">
              Refresh
            </el-button>

            <el-button
              type="success"
              :icon="CircleCheckFilled"
              :loading="loadingHealth"
              @click="runHealthCheck"
            >
              Run Health Check
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
        <template #title>DB 상세 조회 실패</template>
        <p>{{ overviewError }}</p>
      </el-alert>

      <div v-loading="loadingOverview" class="detail-summary-grid">
        <div class="summary-tile">
          <span class="summary-label">Database ID</span>
          <strong>{{ databaseId }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Name</span>
          <strong>{{ database?.name ?? "-" }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Engine</span>
          <el-tag :type="getEngineTagType(database?.engine)" effect="light">
            {{ database?.engine ?? "-" }}
          </el-tag>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Inventory Status</span>
          <el-tag :type="getInventoryStatusTagType(database?.status)" effect="light">
            {{ database?.status ?? "-" }}
          </el-tag>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Host</span>
          <strong>{{ database?.host ?? "-" }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Port</span>
          <strong>{{ database?.port ?? "-" }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Database Name</span>
          <strong>{{ database?.databaseName ?? "-" }}</strong>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Environment</span>
          <strong>{{ database?.environment ?? "-" }}</strong>
        </div>
      </div>
    </el-card>

    <el-tabs type="border-card" class="detail-tabs">
      <el-tab-pane label="Overview">
        <div class="tab-toolbar">
          <h3>Overview</h3>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="Service Name">
            {{ formatNullable(database?.serviceName) }}
          </el-descriptions-item>

          <el-descriptions-item label="Owner">
            {{ formatNullable(database?.owner) }}
          </el-descriptions-item>

          <el-descriptions-item label="Description" :span="2">
            {{ formatNullable(database?.description) }}
          </el-descriptions-item>

          <el-descriptions-item label="Latest Health">
            <el-tag :type="getHealthTagType(health?.status)" effect="light">
              {{ health?.status ?? "Not checked" }}
            </el-tag>
          </el-descriptions-item>

          <el-descriptions-item label="Health Response Time">
            {{ health?.responseTimeMs ?? "-" }}ms
          </el-descriptions-item>

          <el-descriptions-item label="Health Message" :span="2">
            {{ health?.message ?? "Run Health Check to verify connection." }}
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <el-tab-pane label="Health / Basic">
        <div class="tab-toolbar">
          <h3>Health / Basic Diagnostics</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingDiagnostics"
            @click="loadBasicDiagnostics"
          >
            Refresh
          </el-button>
        </div>

        <el-alert
          v-if="diagnosticsError"
          type="error"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>Basic Diagnostics 조회 실패</template>
          <p>{{ diagnosticsError }}</p>
        </el-alert>

        <div v-loading="loadingDiagnostics" class="metric-grid">
          <div class="metric-card">
            <span class="metric-label">Version</span>
            <strong>{{ version?.version ?? "-" }}</strong>
            <small>{{ version?.engine ?? database?.engine ?? "-" }}</small>
          </div>

          <div class="metric-card">
            <span class="metric-label">Uptime</span>
            <strong>{{ formatSeconds(uptime?.uptimeSeconds) }}</strong>
            <small>{{ uptime?.uptimeSeconds ?? "-" }} seconds</small>
          </div>

          <div class="metric-card">
            <span class="metric-label">Current Connections</span>
            <strong>{{ connections?.currentConnections ?? "-" }}</strong>
            <small>running {{ connections?.runningConnections ?? "-" }}</small>
          </div>

          <div class="metric-card">
            <span class="metric-label">Connection Usage</span>
            <strong>{{ connections?.usagePercent ?? "-" }}%</strong>
            <small>max {{ connections?.maxConnections ?? "-" }}</small>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Sessions">
        <div class="tab-toolbar">
          <h3>Sessions</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingSessions"
            @click="loadSessions"
          >
            Refresh
          </el-button>
        </div>

        <el-alert
          v-if="sessionsError"
          type="error"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>Sessions 조회 실패</template>
          <p>{{ sessionsError }}</p>
        </el-alert>

        <el-table
          v-loading="loadingSessions"
          :data="sessions"
          border
          stripe
          empty-text="현재 조회된 세션이 없습니다."
        >
          <el-table-column prop="processId" label="Process ID" width="120" />
          <el-table-column prop="user" label="User" width="150" />
          <el-table-column prop="host" label="Host" min-width="180" />
          <el-table-column prop="databaseName" label="Database" width="150" />
          <el-table-column prop="command" label="Command" width="130" />
          <el-table-column prop="timeSeconds" label="Time" width="110">
            <template #default="{ row }">
              {{ formatSeconds(row.timeSeconds) }}
            </template>
          </el-table-column>
          <el-table-column prop="state" label="State" min-width="180" />
          <el-table-column prop="queryPreview" label="Query Preview" min-width="320" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Long Transactions">
        <div class="tab-toolbar">
          <h3>Long Transactions</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingLongTransactions"
            @click="loadLongTransactions"
          >
            Refresh
          </el-button>
        </div>

        <el-alert
          v-if="longTransactionsError"
          type="error"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>Long Transactions 조회 실패</template>
          <p>{{ longTransactionsError }}</p>
        </el-alert>

        <el-table
          v-loading="loadingLongTransactions"
          :data="longTransactions"
          border
          stripe
          empty-text="장기 실행 Transaction이 없습니다."
        >
          <el-table-column prop="transactionId" label="Transaction ID" min-width="160" />
          <el-table-column prop="state" label="State" width="140" />
          <el-table-column prop="startedAt" label="Started At" min-width="180" />
          <el-table-column prop="durationSeconds" label="Duration" width="140">
            <template #default="{ row }">
              {{ formatSeconds(row.durationSeconds) }}
            </template>
          </el-table-column>
          <el-table-column prop="threadId" label="Thread ID" width="120" />
          <el-table-column prop="queryPreview" label="Query Preview" min-width="320" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Lock Waits">
        <div class="tab-toolbar">
          <h3>Lock Waits</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingLockWaits"
            @click="loadLockWaits"
          >
            Refresh
          </el-button>
        </div>

        <el-alert
          v-if="lockWaitsError"
          type="error"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>Lock Waits 조회 실패</template>
          <p>{{ lockWaitsError }}</p>
        </el-alert>

        <el-table
          v-loading="loadingLockWaits"
          :data="lockWaits"
          border
          stripe
          empty-text="현재 Lock Wait 관계가 없습니다."
        >
          <el-table-column prop="waitingTransactionId" label="Waiting Trx" min-width="160" />
          <el-table-column prop="waitingThreadId" label="Waiting Thread" width="150" />
          <el-table-column prop="waitingQueryPreview" label="Waiting Query" min-width="300" show-overflow-tooltip />
          <el-table-column prop="blockingTransactionId" label="Blocking Trx" min-width="160" />
          <el-table-column prop="blockingThreadId" label="Blocking Thread" width="150" />
          <el-table-column prop="blockingQueryPreview" label="Blocking Query" min-width="300" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Slow Queries">
        <div class="tab-toolbar">
          <h3>Slow Query Candidates</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingSlowQueries"
            @click="loadSlowQueries"
          >
            Refresh
          </el-button>
        </div>

        <el-alert
          v-if="slowQueriesError"
          type="warning"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>Slow Query 조회 불가</template>
          <p>{{ slowQueriesError }}</p>
          <p class="muted-text">
            {{ getUnsupportedMessage("Slow Query Analysis") }}
          </p>
        </el-alert>

        <el-table
          v-loading="loadingSlowQueries"
          :data="slowQueries"
          border
          stripe
          empty-text="Slow Query 후보가 없습니다."
        >
          <el-table-column prop="digestText" label="Digest Text" min-width="360" show-overflow-tooltip />
          <el-table-column prop="executionCount" label="Execution Count" width="160" />
          <el-table-column prop="averageSeconds" label="Avg Seconds" width="140" />
          <el-table-column prop="maxSeconds" label="Max Seconds" width="140" />
          <el-table-column prop="rowsExamined" label="Rows Examined" width="160" />
          <el-table-column prop="rowsSent" label="Rows Sent" width="130" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Drift">
        <div class="tab-toolbar">
          <h3>Latest Configuration Drift</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingDrift"
            @click="loadLatestDrift"
          >
            Refresh
          </el-button>
        </div>

        <el-alert
          v-if="driftError"
          type="warning"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>Latest Drift 조회 불가</template>
          <p>{{ driftError }}</p>
        </el-alert>

        <div v-if="latestDrift" v-loading="loadingDrift" class="section-stack">
          <div class="metric-grid">
            <div class="metric-card">
              <span class="metric-label">Status</span>
              <el-tag :type="getHealthTagType(latestDrift.status)" effect="dark">
                {{ latestDrift.status }}
              </el-tag>
              <small>driftId={{ latestDrift.driftId }}</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">Total</span>
              <strong>{{ latestDrift.totalCount }}</strong>
              <small>checked parameters</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">Compliant</span>
              <strong>{{ latestDrift.compliantCount }}</strong>
              <small>expected = actual</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">Non-Compliant / Missing</span>
              <strong>{{ latestDrift.nonCompliantCount }} / {{ latestDrift.missingCount }}</strong>
              <small>{{ latestDrift.checkedAt }}</small>
            </div>
          </div>

          <el-table
            :data="latestDrift.items"
            border
            stripe
            empty-text="Drift Item이 없습니다."
          >
            <el-table-column prop="parameterName" label="Parameter" min-width="180" />
            <el-table-column prop="expectedValue" label="Expected" min-width="140" />
            <el-table-column prop="actualValue" label="Actual" min-width="140" />
            <el-table-column prop="valueType" label="Type" width="120" />
            <el-table-column prop="dynamic" label="Dynamic" width="100" />
            <el-table-column prop="applyAllowed" label="Apply Allowed" width="130" />
            <el-table-column prop="complianceStatus" label="Status" width="160">
              <template #default="{ row }">
                <el-tag :type="getHealthTagType(row.complianceStatus)" effect="light">
                  {{ row.complianceStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="Message" min-width="300" show-overflow-tooltip />
          </el-table>
        </div>

        <el-empty
          v-else-if="!loadingDrift && !driftError"
          description="최신 Configuration Drift 결과가 없습니다."
        />
      </el-tab-pane>

      <el-tab-pane label="Restore Verification">
        <div class="tab-toolbar">
          <h3>Latest Restore Verification</h3>

          <el-button
            size="small"
            :icon="Refresh"
            :loading="loadingRestoreVerification"
            @click="loadLatestRestoreVerification"
          >
            Refresh
          </el-button>
        </div>

        <el-alert
          v-if="restoreVerificationError"
          type="warning"
          show-icon
          :closable="false"
          class="page-alert"
        >
          <template #title>Restore Verification 조회 불가</template>
          <p>{{ restoreVerificationError }}</p>
        </el-alert>

        <div
          v-if="latestRestoreVerification"
          v-loading="loadingRestoreVerification"
          class="section-stack"
        >
          <div class="metric-grid">
            <div class="metric-card">
              <span class="metric-label">Status</span>
              <el-tag
                :type="getHealthTagType(latestRestoreVerification.status)"
                effect="dark"
              >
                {{ latestRestoreVerification.status }}
              </el-tag>
              <small>verificationId={{ latestRestoreVerification.id }}</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">Source DB</span>
              <strong>{{ latestRestoreVerification.sourceDatabaseName }}</strong>
              <small>{{ latestRestoreVerification.temporaryDatabaseName }}</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">Tables</span>
              <strong>
                {{ latestRestoreVerification.checkedTableCount }} /
                {{ latestRestoreVerification.restoredTableCount }}
              </strong>
              <small>checked / restored</small>
            </div>

            <div class="metric-card">
              <span class="metric-label">Total Rows</span>
              <strong>{{ latestRestoreVerification.totalRowCount }}</strong>
              <small>{{ latestRestoreVerification.completedAt ?? "-" }}</small>
            </div>
          </div>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="Backup File">
              {{ latestRestoreVerification.backupFile }}
            </el-descriptions-item>

            <el-descriptions-item label="Error">
              {{ latestRestoreVerification.errorCode ?? "-" }}
              {{ latestRestoreVerification.errorMessage ?? "" }}
            </el-descriptions-item>
          </el-descriptions>

          <el-table
            :data="latestRestoreVerification.items"
            border
            stripe
            empty-text="Restore Verification Item이 없습니다."
          >
            <el-table-column prop="tableName" label="Table" min-width="180" />
            <el-table-column prop="existsInRestoredDb" label="Exists" width="110" />
            <el-table-column prop="rowCount" label="Row Count" width="130" />
            <el-table-column prop="status" label="Status" width="150">
              <template #default="{ row }">
                <el-tag :type="getHealthTagType(row.status)" effect="light">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="Message" min-width="300" show-overflow-tooltip />
          </el-table>
        </div>

        <el-empty
          v-else-if="!loadingRestoreVerification && !restoreVerificationError"
          description="최신 Restore Verification 결과가 없습니다."
        />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>