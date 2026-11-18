<script setup lang="ts">
import { operationalLabel } from "../labels";
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
    ElMessage.error("실행기 상세 정보를 불러오지 못했습니다.");
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
            <span>실행기 상세 #{{ route.params.agentId }}</span>
          </div>

          <div class="card-actions">
            <el-button :icon="ArrowLeft" @click="goBack">
              목록
            </el-button>
            <el-button :icon="Refresh" :loading="loading" @click="loadAgentDetail">
              새로고침
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
          <span class="summary-label">실행기 이름</span>
          <strong>{{ agent.agentName }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">상태</span>
          <strong>
            <el-tag :type="getStatusTagType(agent.status)">
              {{ operationalLabel(agent.status) }}
            </el-tag>
          </strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">호스트</span>
          <strong>{{ agent.hostname }}</strong>
          <small class="muted-text">{{ agent.ipAddress }}</small>
        </div>
        <div class="summary-tile">
          <span class="summary-label">실행 환경</span>
          <strong>{{ agent.osName }}</strong>
          <small class="muted-text">{{ agent.architecture }} / {{ agent.agentVersion }}</small>
        </div>
        <div class="summary-tile">
          <span class="summary-label">마지막 접속</span>
          <strong>{{ formatNullable(agent.lastHeartbeatAt) }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">마지막 접속 후 경과</span>
          <strong>{{ formatHeartbeatDelay(agent.heartbeatDelaySeconds) }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">등록 시각</span>
          <strong>{{ formatNullable(agent.createdAt) }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">수정 시각</span>
          <strong>{{ formatNullable(agent.updatedAt) }}</strong>
        </div>
      </div>

      <el-empty
        v-else-if="!loading"
        description="실행기 정보 없음"
      />
    </el-card>

    <el-tabs
      v-if="agent"
      class="detail-tabs"
      type="border-card"
    >
      <el-tab-pane label="서버 자원">
        <div class="tab-toolbar">
          <h3>최근 자원 사용량</h3>
        </div>

        <el-table scrollbar-tabindex="0"
          class="console-table"
          :data="detail?.recentHostMetrics ?? []"
          empty-text="수집된 자원 사용량 없음"
        >
          <el-table-column prop="metricId" label="측정 번호" width="110" />
          <el-table-column label="CPU" width="120">
            <template #default="{ row }">
              {{ formatPercent(row.cpuUsagePercent) }}
            </template>
          </el-table-column>
          <el-table-column label="메모리" width="120">
            <template #default="{ row }">
              {{ formatPercent(row.memoryUsagePercent) }}
            </template>
          </el-table-column>
          <el-table-column label="디스크" width="120">
            <template #default="{ row }">
              {{ formatPercent(row.diskUsagePercent) }}
            </template>
          </el-table-column>
          <el-table-column prop="collectedAt" label="수집 시각" min-width="190" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="실행 단계">
        <div class="tab-toolbar">
          <h3>최근 실행 단계</h3>
        </div>

        <el-table scrollbar-tabindex="0"
          class="console-table"
          :data="detail?.recentOperationTasks ?? []"
          empty-text="할당된 실행 단계 없음"
        >
          <el-table-column prop="taskId" label="단계 번호" width="100" />
          <el-table-column prop="taskType" label="단계 유형" min-width="210">
            <template #default="{ row }">{{ operationalLabel(row.taskType) }}</template>
          </el-table-column>
          <el-table-column label="작업 번호" width="120">
            <template #default="{ row }">
              {{ formatNullable(row.operationJobId) }}
            </template>
          </el-table-column>
          <el-table-column label="상태" width="120">
            <template #default="{ row }">
              <el-tag :type="getTaskStatusTagType(row.status)">
                {{ operationalLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startedAt" label="시작 시각" min-width="180" />
          <el-table-column prop="completedAt" label="완료 시각" min-width="180" />
          <el-table-column prop="errorCode" label="오류 코드" min-width="160" />
          <el-table-column label="관리" width="130">
            <template #default="{ row }">
              <el-button
                v-if="row.operationJobId"
                type="primary"
                plain
                size="small"
                :icon="View"
                @click="openJob(row.operationJobId)"
              >
                작업
              </el-button>
              <span v-else class="muted-text">-</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
