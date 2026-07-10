<script setup lang="ts">
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
    ElMessage.error("Agent 목록을 불러오지 못했습니다.");
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
            <span>Agents</span>
            <p class="card-subtitle">Agent 상태, Heartbeat, Host 정보를 확인합니다.</p>
          </div>

          <div class="card-actions">
            <el-button :icon="Refresh" :loading="loading" @click="loadAgents">
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

      <el-table
        v-if="hasAgents || loading"
        v-loading="loading"
        class="console-table"
        :data="agents"
      >
        <el-table-column prop="agentId" label="Agent ID" width="100" />

        <el-table-column label="Agent Name" min-width="190">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.agentName }}</strong>
              <small>{{ row.hostname }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="Host/IP" min-width="210">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.hostname }}</strong>
              <small>{{ row.ipAddress }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="OS/Architecture" min-width="190">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.osName }}</strong>
              <small>{{ row.architecture }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="agentVersion" label="Version" width="120" />

        <el-table-column label="Status" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Last Heartbeat" min-width="190">
          <template #default="{ row }">
            {{ formatNullable(row.lastHeartbeatAt) }}
          </template>
        </el-table-column>

        <el-table-column label="Heartbeat Delay" width="150">
          <template #default="{ row }">
            {{ formatHeartbeatDelay(row.heartbeatDelaySeconds) }}
          </template>
        </el-table-column>

        <el-table-column label="Actions" width="130" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                type="primary"
                plain
                size="small"
                :icon="View"
                @click="openAgentDetail(row.agentId)"
              >
                Detail
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-else
        description="No agents registered yet."
      />
    </el-card>
  </section>
</template>
