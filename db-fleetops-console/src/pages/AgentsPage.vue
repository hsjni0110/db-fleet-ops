<script setup lang="ts">
import { operationalLabel } from "../labels";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Refresh, View } from "@element-plus/icons-vue";

import { getAgents, getApiErrorMessage } from "../api";
import type { AgentConsoleResponse, AgentStatus } from "../types";

const router = useRouter();

const agents = ref<AgentConsoleResponse[]>([]);
const loading = ref(false);
const errorMessage = ref<string | null>(null);

const hasAgents = computed(() => agents.value.length > 0);

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

function formatNullable(value?: string | number | null) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return String(value);
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

async function loadAgents() {
  loading.value = true;
  errorMessage.value = null;

  try {
    agents.value = await getAgents();
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error);
    ElMessage.error("실행기 목록을 불러오지 못했습니다.");
  } finally {
    loading.value = false;
  }
}

function openAgentDetail(agentId: number) {
  router.push({
    name: "AgentDetail",
    params: {
      agentId,
    },
  });
}

onMounted(loadAgents);
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>실행기 목록</span>
          </div>

          <div class="card-actions">
            <el-button :icon="Refresh" :loading="loading" @click="loadAgents">
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

      <el-table scrollbar-tabindex="0"
        v-if="hasAgents || loading"
        v-loading="loading"
        class="console-table"
        :data="agents"
      >
        <el-table-column prop="agentId" label="실행기 번호" width="100" />

        <el-table-column label="실행기 이름" min-width="190">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.agentName }}</strong>
              <small>{{ row.hostname }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="호스트 주소" min-width="210">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.hostname }}</strong>
              <small>{{ row.ipAddress }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="운영체제·아키텍처" min-width="190">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.osName }}</strong>
              <small>{{ row.architecture }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="agentVersion" label="버전" width="120" />

        <el-table-column label="상태" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ operationalLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="마지막 접속" min-width="190">
          <template #default="{ row }">
            {{ formatNullable(row.lastHeartbeatAt) }}
          </template>
        </el-table-column>

        <el-table-column label="마지막 접속 후 경과" width="150">
          <template #default="{ row }">
            {{ formatHeartbeatDelay(row.heartbeatDelaySeconds) }}
          </template>
        </el-table-column>

        <el-table-column label="관리" width="130">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                type="primary"
                plain
                size="small"
                :icon="View"
                @click="openAgentDetail(row.agentId)"
              >
                상세
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-else
        description="등록된 실행기 없음"
      />
    </el-card>
  </section>
</template>
