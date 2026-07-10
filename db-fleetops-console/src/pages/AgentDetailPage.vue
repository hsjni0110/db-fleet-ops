<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowLeft, Refresh, View } from "@element-plus/icons-vue";

import { getAgentDetail, getApiErrorMessage } from "../api";
import type {
  AgentDetailResponse,
  AgentStatus,
  AgentTaskResponse,
} from "../types";

const route = useRoute();
const router = useRouter();

const detail = ref<AgentDetailResponse | null>(null);
const loading = ref(false);
const errorMessage = ref<string | null>(null);

const agentId = computed(() => {
  const rawAgentId = route.params.agentId;
  const normalizedAgentId = Array.isArray(rawAgentId) ? rawAgentId[0] : rawAgentId;
  const parsedAgentId = Number(normalizedAgentId);

  if (!Number.isInteger(parsedAgentId) || parsedAgentId <= 0) {
    return null;
  }

  return parsedAgentId;
});

const agent = computed(() => detail.value?.agent ?? null);

function getStatusTagType(status: AgentStatus) {
  if (status === "ONLINE") {
    return "success";
  }

  if (status === "OFFLINE") {
    return "warning";
  }

  if (status === "DISABLED") {
    return "danger";
  }

  return "info";
}

function getTaskStatusTagType(status: AgentTaskResponse["status"]) {
  if (status === "SUCCEEDED") {
    return "success";
  }

  if (status === "FAILED" || status === "CANCELLED") {
    return "danger";
  }

  if (status === "RUNNING") {
    return "warning";
  }

  return "info";
}

function formatNullable(value?: string | number | null) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return String(value);
}

function formatPercent(value?: number | null) {
  if (value === null || value === undefined) {
    return "-";
  }

  return `${value.toFixed(1)}%`;
}

function formatHeartbeatDelay(seconds?: number | null) {
  if (seconds === null || seconds === undefined) {
    return "-";
  }

  if (seconds < 60) {
    return `${seconds}s`;
  }

  const minutes = Math.floor(seconds / 60);

  if (minutes < 60) {
    return `${minutes}m`;
  }

  const hours = Math.floor(minutes / 60);

  if (hours < 24) {
    return `${hours}h`;
  }

  return `${Math.floor(hours / 24)}d`;
}

async function loadAgentDetail() {
  if (!agentId.value) {
    errorMessage.value = "Invalid agent id.";
    detail.value = null;
    return;
  }

  loading.value = true;
  errorMessage.value = null;

  try {
    detail.value = await getAgentDetail(agentId.value);
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error);
    ElMessage.error("Agent 상세 정보를 불러오지 못했습니다.");
  } finally {
    loading.value = false;
  }
}

function goBack() {
  router.push({ name: "Agents" });
}

function openJob(operationJobId: number | null) {
  if (!operationJobId) {
    return;
  }

  router.push({
    name: "OperationJobDetail",
    params: {
      jobId: operationJobId,
    },
  });
}

onMounted(loadAgentDetail);
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>Agent Detail #{{ route.params.agentId }}</span>
            <p class="card-subtitle">Agent 기본 정보와 최근 Host Metric, Operation Task를 확인합니다.</p>
          </div>

          <div class="card-actions">
            <el-button :icon="ArrowLeft" @click="goBack">
              Back
            </el-button>
            <el-button :icon="Refresh" :loading="loading" @click="loadAgentDetail">
              Refresh
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="errorMessage"
        class="page-alert"
        type="error"
        :title="errorMessage"
        show-icon
        :closable="false"
      />

      <div v-if="agent" v-loading="loading" class="detail-summary-grid">
        <div class="summary-tile">
          <span class="summary-label">Agent Name</span>
          <strong>{{ agent.agentName }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Status</span>
          <strong>
            <el-tag :type="getStatusTagType(agent.status)">
              {{ agent.status }}
            </el-tag>
          </strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Host</span>
          <strong>{{ agent.hostname }}</strong>
          <small class="muted-text">{{ agent.ipAddress }}</small>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Runtime</span>
          <strong>{{ agent.osName }}</strong>
          <small class="muted-text">{{ agent.architecture }} / {{ agent.agentVersion }}</small>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Last Heartbeat</span>
          <strong>{{ formatNullable(agent.lastHeartbeatAt) }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Heartbeat Delay</span>
          <strong>{{ formatHeartbeatDelay(agent.heartbeatDelaySeconds) }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Created At</span>
          <strong>{{ formatNullable(agent.createdAt) }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Updated At</span>
          <strong>{{ formatNullable(agent.updatedAt) }}</strong>
        </div>
      </div>

      <el-empty
        v-else-if="!loading"
        description="Agent detail is not available."
      />
    </el-card>

    <el-tabs
      v-if="agent"
      class="detail-tabs"
      type="border-card"
    >
      <el-tab-pane label="Host Metrics">
        <div class="tab-toolbar">
          <h3>Recent Host Metrics</h3>
        </div>

        <el-table
          class="console-table"
          :data="detail?.recentHostMetrics ?? []"
          empty-text="No host metrics collected yet."
        >
          <el-table-column prop="metricId" label="Metric ID" width="110" />
          <el-table-column label="CPU" width="120">
            <template #default="{ row }">
              {{ formatPercent(row.cpuUsagePercent) }}
            </template>
          </el-table-column>
          <el-table-column label="Memory" width="120">
            <template #default="{ row }">
              {{ formatPercent(row.memoryUsagePercent) }}
            </template>
          </el-table-column>
          <el-table-column label="Disk" width="120">
            <template #default="{ row }">
              {{ formatPercent(row.diskUsagePercent) }}
            </template>
          </el-table-column>
          <el-table-column prop="collectedAt" label="Collected At" min-width="190" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Operation Tasks">
        <div class="tab-toolbar">
          <h3>Recent Operation Tasks</h3>
        </div>

        <el-table
          class="console-table"
          :data="detail?.recentOperationTasks ?? []"
          empty-text="No operation tasks assigned yet."
        >
          <el-table-column prop="taskId" label="Task ID" width="100" />
          <el-table-column prop="taskType" label="Task Type" min-width="210" />
          <el-table-column label="Job ID" width="120">
            <template #default="{ row }">
              {{ formatNullable(row.operationJobId) }}
            </template>
          </el-table-column>
          <el-table-column label="Status" width="120">
            <template #default="{ row }">
              <el-tag :type="getTaskStatusTagType(row.status)">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startedAt" label="Started At" min-width="180" />
          <el-table-column prop="completedAt" label="Completed At" min-width="180" />
          <el-table-column prop="errorCode" label="Error Code" min-width="160" />
          <el-table-column label="Actions" width="130" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.operationJobId"
                type="primary"
                plain
                size="small"
                :icon="View"
                @click="openJob(row.operationJobId)"
              >
                Job
              </el-button>
              <span v-else class="muted-text">-</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
