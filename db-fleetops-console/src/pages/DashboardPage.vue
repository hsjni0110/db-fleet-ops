<script setup lang="ts">
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
            <span>Dashboard Overview</span>
            <p class="card-subtitle">DB FleetOps의 현재 운영 상태를 한눈에 확인합니다.</p>
          </div>
          <div class="card-actions">
            <el-tag type="primary">Operations Console</el-tag>
            <el-button
              :icon="Refresh"
              :loading="loadingDatabases || loadingJobs || loadingAgents"
              @click="refreshDashboard"
            >
              Refresh All
            </el-button>
          </div>
        </div>
      </template>

      <div class="dashboard-metric-grid">
        <div class="metric-card"><span class="metric-label">Total Databases</span><strong>{{ databases.length }}</strong><small>{{ activeDatabases.length }} active</small></div>
        <div class="metric-card"><span class="metric-label">Healthy</span><strong>{{ countHealth("HEALTHY") }}</strong><small>Active databases</small></div>
        <div class="metric-card"><span class="metric-label">Degraded</span><strong>{{ countHealth("DEGRADED") }}</strong><small>Needs attention</small></div>
        <div class="metric-card"><span class="metric-label">Critical</span><strong>{{ countHealth("CRITICAL") }}</strong><small>Immediate action</small></div>
        <div class="metric-card"><span class="metric-label">Online Agents</span><strong>{{ onlineAgents }}</strong><small>{{ agents.length }} registered</small></div>
        <div class="metric-card"><span class="metric-label">Queued Jobs</span><strong>{{ countJobs("QUEUED") }}</strong><small>Waiting for worker</small></div>
        <div class="metric-card"><span class="metric-label">Running Jobs</span><strong>{{ countJobs("RUNNING") }}</strong><small>In progress</small></div>
        <div class="metric-card"><span class="metric-label">Failed Jobs</span><strong>{{ countJobs("FAILED") }}</strong><small>Needs review</small></div>
        <div class="metric-card"><span class="metric-label">Latest Backup</span><strong class="metric-status-value">{{ latestBackup?.status ?? "NO DATA" }}</strong><small>{{ latestBackup ? `Job #${latestBackup.jobId}` : "No backup jobs" }}</small></div>
        <div class="metric-card metric-card-unavailable"><span class="metric-label">Open Alerts</span><strong>N/A</strong><small>Alert API not available</small></div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header><div class="card-header"><div><span>DB Health Summary</span><p class="card-subtitle">ACTIVE DB를 대상으로 실행한 최신 Health Check 결과입니다.</p></div><el-button :icon="View" @click="router.push('/databases')">View Databases</el-button></div></template>
      <el-alert v-if="databasesError" class="page-alert" type="error" :title="databasesError" show-icon :closable="false" />
      <el-table v-if="healthRows.length || loadingHealth" v-loading="loadingHealth" class="console-table" :data="healthRows">
        <el-table-column label="Database" min-width="180"><template #default="{ row }"><div class="table-primary-cell"><strong>{{ row.database.name }}</strong><small>#{{ row.database.id }}</small></div></template></el-table-column>
        <el-table-column prop="database.engine" label="Engine" width="130" />
        <el-table-column prop="database.status" label="Inventory" width="120" />
        <el-table-column label="Health" width="130"><template #default="{ row }"><el-tag :type="getHealthTagType(row.result?.status)">{{ row.result?.status ?? "UNKNOWN" }}</el-tag></template></el-table-column>
        <el-table-column label="Response" width="120"><template #default="{ row }">{{ row.result ? `${row.result.responseTimeMs} ms` : "-" }}</template></el-table-column>
        <el-table-column label="Checked At" min-width="180"><template #default="{ row }">{{ formatDateTime(row.result?.checkedAt) }}</template></el-table-column>
        <el-table-column label="Result" min-width="240"><template #default="{ row }"><span :class="row.error ? 'dashboard-error-text' : ''">{{ row.error ?? row.result?.message ?? "-" }}</span></template></el-table-column>
      </el-table>
      <el-empty v-else description="Health Check를 실행할 ACTIVE DB가 없습니다." />
    </el-card>

    <div class="dashboard-section-grid">
      <el-card shadow="never">
        <template #header><div class="card-header"><div><span>Recent Operation Jobs</span><p class="card-subtitle">최근 생성된 Job 5건입니다.</p></div><el-button :icon="View" @click="router.push('/jobs')">View All</el-button></div></template>
        <el-alert v-if="jobsError" class="page-alert" type="error" :title="jobsError" show-icon :closable="false" />
        <el-table v-if="recentJobs.length || loadingJobs" v-loading="loadingJobs" class="console-table" :data="recentJobs" @row-click="(row: OperationJobResponse) => router.push(`/jobs/${row.jobId}`)">
          <el-table-column label="Job" width="85"><template #default="{ row }"><strong>#{{ row.jobId }}</strong></template></el-table-column>
          <el-table-column prop="jobType" label="Type" min-width="160" />
          <el-table-column label="Status" width="125"><template #default="{ row }"><OperationJobStatusTag :status="row.status" /></template></el-table-column>
          <el-table-column label="Created" min-width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        </el-table>
        <el-empty v-else description="Operation Job이 없습니다." />
      </el-card>

      <el-card shadow="never">
        <template #header><div class="card-header"><div><span>Agent Summary</span><p class="card-subtitle">Agent 연결 상태 요약입니다.</p></div><el-button :icon="View" @click="router.push('/agents')">View Agents</el-button></div></template>
        <el-alert v-if="agentsError" class="page-alert" type="error" :title="agentsError" show-icon :closable="false" />
        <div v-if="agents.length || loadingAgents" v-loading="loadingAgents" class="agent-summary-list">
          <div v-for="item in [{ status: 'ONLINE', count: onlineAgents }, { status: 'OFFLINE', count: offlineAgents }, { status: 'UNKNOWN', count: unknownAgents }, { status: 'DISABLED', count: disabledAgents }]" :key="item.status" class="agent-summary-row">
            <el-tag :type="getAgentTagType(item.status)">{{ item.status }}</el-tag><strong>{{ item.count }}</strong>
          </div>
        </div>
        <el-empty v-else description="등록된 Agent가 없습니다." />
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header><div class="card-header"><div><span>Recent Alerts</span><p class="card-subtitle">Alert API 연동 전까지 사용할 수 없는 영역입니다.</p></div><el-button :icon="View" @click="router.push('/alerts')">Open Alerts Page</el-button></div></template>
      <el-alert title="Alert API not available" description="Alert 도메인과 조회 API는 후속 구현 범위입니다. 임시 데이터는 표시하지 않습니다." type="info" show-icon :closable="false" />
    </el-card>
  </section>
</template>
