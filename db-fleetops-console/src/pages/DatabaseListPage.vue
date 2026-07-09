<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  CircleCheckFilled,
  Connection,
  Delete,
  Refresh,
  View,
} from "@element-plus/icons-vue";

import {
  deactivateDatabaseInstance,
  getDatabaseInstances,
  runDatabaseHealthCheck,
} from "../api";
import type {
  DatabaseHealthStatus,
  DatabaseInstanceSummary,
  DatabaseStatus,
  InventoryHealthCheckResponse,
} from "../types";

const router = useRouter();

const databases = ref<DatabaseInstanceSummary[]>([]);
const loading = ref(false);
const errorMessage = ref<string | null>(null);
const healthCheckLoadingId = ref<number | null>(null);
const lastHealthResults = ref<Record<number, InventoryHealthCheckResponse>>({});

const hasDatabases = computed(() => {
  return databases.value.length > 0;
});

function getEngineTagType(engine: string) {
  if (engine === "MYSQL") {
    return "primary";
  }

  if (engine === "POSTGRESQL") {
    return "success";
  }

  return "info";
}

function getStatusTagType(status: DatabaseStatus) {
  if (status === "ACTIVE") {
    return "success";
  }

  if (status === "INACTIVE") {
    return "info";
  }

  return "warning";
}

function getHealthTagType(status?: DatabaseHealthStatus) {
  if (!status) {
    return "info";
  }

  if (status === "HEALTHY" || status === "UP") {
    return "success";
  }

  if (status === "DEGRADED") {
    return "warning";
  }

  if (status === "CRITICAL" || status === "DOWN") {
    return "danger";
  }

  return "info";
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message;
  }

  return "Unknown error occurred.";
}

async function loadDatabases() {
  loading.value = true;
  errorMessage.value = null;

  try {
    databases.value = await getDatabaseInstances();
  } catch (error) {
    errorMessage.value = getErrorMessage(error);
    ElMessage.error("DB 목록을 불러오지 못했습니다.");
  } finally {
    loading.value = false;
  }
}

async function handleHealthCheck(database: DatabaseInstanceSummary) {
  healthCheckLoadingId.value = database.id;

  try {
    const result = await runDatabaseHealthCheck(database.id);

    lastHealthResults.value = {
      ...lastHealthResults.value,
      [database.id]: result,
    };

    ElMessage.success(
      `${database.name} Health Check completed: ${result.status}`,
    );
  } catch (error) {
    ElMessage.error(
      `${database.name} Health Check failed. ${getErrorMessage(error)}`,
    );
  } finally {
    healthCheckLoadingId.value = null;
  }
}

function handleViewDetail(database: DatabaseInstanceSummary) {
  router.push(`/databases/${database.id}`);
}

async function handleDeactivate(database: DatabaseInstanceSummary) {
  try {
    await ElMessageBox.confirm(
      `${database.name} DB 인스턴스를 비활성화하시겠습니까? 삭제가 아니라 INACTIVE 상태로 변경됩니다.`,
      "DB 비활성화 확인",
      {
        confirmButtonText: "비활성화",
        cancelButtonText: "취소",
        type: "warning",
      },
    );

    await deactivateDatabaseInstance(database.id);

    ElMessage.success(`${database.name} DB 인스턴스를 비활성화했습니다.`);

    await loadDatabases();
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }

    ElMessage.error(
      `${database.name} 비활성화에 실패했습니다. ${getErrorMessage(error)}`,
    );
  }
}

onMounted(() => {
  loadDatabases();
});
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>Database Inventory</span>
            <p class="card-subtitle">
              관리 대상 DB 인스턴스와 최근 Health Check 결과를 확인합니다.
            </p>
          </div>

          <div class="card-actions">
            <el-button :icon="Refresh" :loading="loading" @click="loadDatabases">
              Refresh
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="page-alert"
      >
        <template #title>Database Inventory API 호출 실패</template>
        <p>{{ errorMessage }}</p>
      </el-alert>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        class="page-alert"
      >
        <template #title>운영 콘솔 설계 기준</template>
        <p>
          이 화면은 Credential을 표시하지 않습니다. Inventory API 응답에는 비밀번호가
          포함되지 않으며, 화면은 DB 식별 정보와 운영 상태만 표시합니다.
        </p>
      </el-alert>

      <el-table
        v-loading="loading"
        :data="databases"
        border
        stripe
        class="console-table"
        empty-text="등록된 DB 인스턴스가 없습니다."
      >
        <el-table-column prop="id" label="ID" width="90" />

        <el-table-column prop="name" label="Name" min-width="180">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.name }}</strong>
              <small>databaseId={{ row.id }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="engine" label="Engine" width="140">
          <template #default="{ row }">
            <el-tag :type="getEngineTagType(row.engine)" effect="light">
              {{ row.engine }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="Inventory Status" width="160">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" effect="light">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Last Health Check" min-width="220">
          <template #default="{ row }">
            <div
              v-if="lastHealthResults[row.id]"
              class="health-result-cell"
            >
              <el-tag
                :type="getHealthTagType(lastHealthResults[row.id].status)"
                effect="dark"
              >
                {{ lastHealthResults[row.id].status }}
              </el-tag>

              <span>
                {{ lastHealthResults[row.id].responseTimeMs }}ms
              </span>
            </div>

            <span v-else class="muted-text">Not checked in this session</span>
          </template>
        </el-table-column>

        <el-table-column label="Message" min-width="260">
          <template #default="{ row }">
            <span v-if="lastHealthResults[row.id]">
              {{ lastHealthResults[row.id].message }}
            </span>

            <span v-else class="muted-text">
              Run health check to verify connection.
            </span>
          </template>
        </el-table-column>

        <el-table-column label="Actions" fixed="right" width="320">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                size="small"
                type="primary"
                :icon="View"
                @click="handleViewDetail(row)"
              >
                Detail
              </el-button>

              <el-button
                size="small"
                type="success"
                :icon="CircleCheckFilled"
                :loading="healthCheckLoadingId === row.id"
                @click="handleHealthCheck(row)"
              >
                Health
              </el-button>

              <el-button
                size="small"
                type="danger"
                plain
                :icon="Delete"
                :disabled="row.status === 'INACTIVE'"
                @click="handleDeactivate(row)"
              >
                Deactivate
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && !hasDatabases"
        description="등록된 DB 인스턴스가 없습니다. 이후 DB 등록 화면에서 Inventory를 추가할 수 있습니다."
      >
        <el-button type="primary" :icon="Connection">
          Register Database
        </el-button>
      </el-empty>
    </el-card>
  </section>
</template>