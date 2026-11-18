<script setup lang="ts">
import { operationalLabel } from "../labels";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Refresh, View } from "@element-plus/icons-vue";

import {
  getAgents,
  getApiErrorMessage,
  getDatabaseInstances,
  getOperationJobs,
  runDatabaseHealthCheck,
} from "../api";
import OperationJobStatusTag from "../components/OperationJobStatusTag.vue";
import type {
  AgentConsoleResponse,
  DatabaseHealthStatus,
  DatabaseInstanceSummary,
  InventoryHealthCheckResponse,
  OperationJobResponse,
  OperationJobStatus,
} from "../types";

interface HealthRow {
  database: DatabaseInstanceSummary;
  result?: InventoryHealthCheckResponse;
  error?: string;
}

const router = useRouter();
const databases = ref<DatabaseInstanceSummary[]>([]);
const jobs = ref<OperationJobResponse[]>([]);
const agents = ref<AgentConsoleResponse[]>([]);
const healthRows = ref<HealthRow[]>([]);
const loadingDatabases = ref(false);
const loadingJobs = ref(false);
const loadingAgents = ref(false);
const loadingHealth = ref(false);
const databasesError = ref<string | null>(null);
const jobsError = ref<string | null>(null);
const agentsError = ref<string | null>(null);

const activeDatabases = computed(() =>
  databases.value.filter((database) => database.status === "ACTIVE"),
);
const recentJobs = computed(() => jobs.value.slice(0, 5));
const onlineAgents = computed(() =>
  agents.value.filter((agent) => agent.status === "ONLINE").length,
);
const offlineAgents = computed(() =>
  agents.value.filter((agent) => agent.status === "OFFLINE").length,
);
const unknownAgents = computed(() =>
  agents.value.filter((agent) => agent.status === "UNKNOWN").length,
);
const disabledAgents = computed(() =>
  agents.value.filter((agent) => agent.status === "DISABLED").length,
);
const latestBackup = computed(() =>
  jobs.value.find((job) => job.jobType === "BACKUP"),
);

function countHealth(status: DatabaseHealthStatus) {
  return healthRows.value.filter((row) => row.result?.status === status).length;
}

function countJobs(status: OperationJobStatus) {
  return jobs.value.filter((job) => job.status === status).length;
}

function getHealthTagType(status?: DatabaseHealthStatus) {
  if (status === "HEALTHY" || status === "UP") return "success";
  if (status === "DEGRADED") return "warning";
  if (status === "CRITICAL" || status === "DOWN") return "danger";
  return "info";
}

function getAgentTagType(status: string) {
  if (status === "ONLINE") return "success";
  if (status === "OFFLINE") return "warning";
  if (status === "DISABLED") return "danger";
  return "info";
}

function formatDateTime(value?: string | null) {
  return value ? value.replace("T", " ") : "-";
}

async function loadHealth() {
  loadingHealth.value = true;
  healthRows.value = activeDatabases.value.map((database) => ({ database }));

  const results = await Promise.all(
    activeDatabases.value.map(async (database): Promise<HealthRow> => {
      try {
        return { database, result: await runDatabaseHealthCheck(database.id) };
      } catch (error) {
        return { database, error: getApiErrorMessage(error) };
      }
    }),
  );

  healthRows.value = results;
  loadingHealth.value = false;
}

async function loadDatabases() {
  loadingDatabases.value = true;
  databasesError.value = null;

  try {
    databases.value = await getDatabaseInstances();
    await loadHealth();
  } catch (error) {
    databases.value = [];
    healthRows.value = [];
    databasesError.value = getApiErrorMessage(error);
  } finally {
    loadingDatabases.value = false;
  }
}

async function loadJobs() {
  loadingJobs.value = true;
  jobsError.value = null;
  try {
    jobs.value = await getOperationJobs();
  } catch (error) {
    jobs.value = [];
    jobsError.value = getApiErrorMessage(error);
  } finally {
    loadingJobs.value = false;
  }
}

async function loadAgents() {
  loadingAgents.value = true;
  agentsError.value = null;
  try {
    agents.value = await getAgents();
  } catch (error) {
    agents.value = [];
    agentsError.value = getApiErrorMessage(error);
  } finally {
    loadingAgents.value = false;
  }
}

async function refreshDashboard() {
  await Promise.all([loadDatabases(), loadJobs(), loadAgents()]);
}

onMounted(refreshDashboard);
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>상태 요약</span>
          </div>
          <div class="card-actions">
            <el-button
              :icon="Refresh"
              :loading="loadingDatabases || loadingJobs || loadingAgents"
              @click="refreshDashboard"
            >
              전체 새로고침
            </el-button>
          </div>
        </div>
      </template>

      <div class="dashboard-metric-grid">
        <div class="metric-card">
          <span class="metric-label">등록 데이터베이스</span>
          <strong>{{ loadingDatabases || databasesError ? "—" : databases.length }}</strong>
          <small v-if="!loadingDatabases && !databasesError">활성 {{ activeDatabases.length }}개</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">주의·심각 데이터베이스</span>
          <strong>{{ loadingDatabases || loadingHealth || databasesError ? "—" : countHealth("DEGRADED") + countHealth("CRITICAL") }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">대기·실행 작업</span>
          <strong>{{ loadingJobs || jobsError ? "—" : countJobs("QUEUED") + countJobs("RUNNING") }}</strong>
          <small v-if="!loadingJobs && !jobsError">실패 {{ countJobs("FAILED") }}건</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">최근 백업</span>
          <template v-if="latestBackup">
            <OperationJobStatusTag :status="latestBackup.status" />
            <RouterLink :to="`/jobs/${latestBackup.jobId}`" class="table-link">작업 #{{ latestBackup.jobId }}</RouterLink>
          </template>
          <strong v-else class="metric-status-value">{{ loadingJobs || jobsError ? "—" : "이력 없음" }}</strong>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header><div class="card-header"><div><span>데이터베이스 연결 상태</span></div><el-button :icon="View" @click="router.push('/databases')">데이터베이스 목록</el-button></div></template>
      <el-alert v-if="databasesError" class="page-alert" type="error" :title="databasesError" show-icon :closable="false" />
      <el-table scrollbar-tabindex="0" v-if="healthRows.length || loadingHealth" v-loading="loadingHealth" class="console-table" :data="healthRows">
        <el-table-column label="데이터베이스" min-width="180"><template #default="{ row }"><div class="table-primary-cell"><strong>{{ row.database.name }}</strong><small>#{{ row.database.id }}</small></div></template></el-table-column>
        <el-table-column prop="database.engine" label="엔진" width="130">
          <template #default="{ row }">{{ operationalLabel(row.database.engine) }}</template>
        </el-table-column>
        <el-table-column prop="database.status" label="등록 상태" width="120">
          <template #default="{ row }">{{ operationalLabel(row.database.status) }}</template>
        </el-table-column>
        <el-table-column label="연결 점검" width="130"><template #default="{ row }"><el-tag :type="getHealthTagType(row.result?.status)">{{ operationalLabel(row.result?.status ?? "UNKNOWN") }}</el-tag></template></el-table-column>
        <el-table-column label="응답 시간" width="120"><template #default="{ row }">{{ row.result ? `${row.result.responseTimeMs} ms` : "-" }}</template></el-table-column>
        <el-table-column label="점검 시각" min-width="180"><template #default="{ row }">{{ formatDateTime(row.result?.checkedAt) }}</template></el-table-column>
        <el-table-column label="결과" min-width="240"><template #default="{ row }"><span :class="row.error ? 'dashboard-error-text' : ''">{{ row.error ?? row.result?.message ?? "-" }}</span></template></el-table-column>
      </el-table>
      <el-empty v-else-if="!databasesError" description="점검할 활성 데이터베이스 없음" />
    </el-card>

    <div class="dashboard-section-grid">
      <el-card shadow="never">
        <template #header><div class="card-header"><div><span>최근 운영 작업</span></div><el-button :icon="View" @click="router.push('/jobs')">작업 목록</el-button></div></template>
        <el-alert v-if="jobsError" class="page-alert" type="error" :title="jobsError" show-icon :closable="false" />
        <el-table scrollbar-tabindex="0" v-if="recentJobs.length || loadingJobs" v-loading="loadingJobs" class="console-table" :data="recentJobs">
          <el-table-column label="작업" width="85"><template #default="{ row }"><RouterLink :to="`/jobs/${row.jobId}`" class="table-link">#{{ row.jobId }}</RouterLink></template></el-table-column>
          <el-table-column prop="jobType" label="유형" min-width="160">
            <template #default="{ row }">{{ operationalLabel(row.jobType) }}</template>
          </el-table-column>
          <el-table-column label="상태" width="125"><template #default="{ row }"><OperationJobStatusTag :status="row.status" /></template></el-table-column>
          <el-table-column label="요청 시각" min-width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        </el-table>
        <el-empty v-else-if="!jobsError" description="운영 작업 없음" />
      </el-card>

      <el-card shadow="never">
        <template #header><div class="card-header"><div><span>실행기 접속 상태</span></div><el-button :icon="View" @click="router.push('/agents')">실행기 목록</el-button></div></template>
        <el-alert v-if="agentsError" class="page-alert" type="error" :title="agentsError" show-icon :closable="false" />
        <div v-if="agents.length || loadingAgents" v-loading="loadingAgents" class="agent-summary-list">
          <div v-for="item in [{ status: 'ONLINE', count: onlineAgents }, { status: 'OFFLINE', count: offlineAgents }, { status: 'UNKNOWN', count: unknownAgents }, { status: 'DISABLED', count: disabledAgents }]" :key="item.status" class="agent-summary-row">
            <el-tag :type="getAgentTagType(item.status)">{{ operationalLabel(item.status) }}</el-tag><strong>{{ item.count }}</strong>
          </div>
        </div>
        <el-empty v-else-if="!agentsError" description="등록된 실행기 없음" />
      </el-card>
    </div>

  </section>
</template>
