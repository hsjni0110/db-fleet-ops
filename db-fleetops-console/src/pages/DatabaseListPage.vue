<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  CircleCheckFilled,
  Connection,
  Delete,
  Plus,
  Refresh,
  View,
} from "@element-plus/icons-vue";

import {
  createDatabaseInstance,
  deactivateDatabaseInstance,
  getApiErrorMessage,
  getDatabaseInstances,
  isDatabaseNotReachableError,
  runDatabaseHealthCheck,
} from "../api";

import type {
  CreateDatabaseInstanceRequest,
  DatabaseEngine,
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

const registerDialogVisible = ref(false);
const registering = ref(false);

const registerForm = reactive<CreateDatabaseInstanceRequest>({
  name: "",
  host: "target-mysql",
  port: 3306,
  databaseName: "orders",
  engine: "MYSQL",
  environment: "LOCAL",
  serviceName: "target-mysql",
  owner: "platform-team",
  description: "Docker Compose target MySQL managed by DB FleetOps",
  username: "root",
  password: "",
});

const hasDatabases = computed(() => {
  return databases.value.length > 0;
});

function resetRegisterForm() {
  registerForm.name = "";
  registerForm.host = "target-mysql";
  registerForm.port = registerForm.engine === "POSTGRESQL" ? 5432 : 3306;
  registerForm.databaseName = "orders";
  registerForm.environment = "LOCAL";
  registerForm.serviceName = "target-mysql";
  registerForm.owner = "platform-team";
  registerForm.description = "Docker Compose target MySQL managed by DB FleetOps";
  registerForm.username = "root";
  registerForm.password = "";
}

function handleEngineChange(engine: DatabaseEngine) {
  if (engine === "MYSQL") {
    registerForm.port = 3306;
  }

  if (engine === "POSTGRESQL") {
    registerForm.port = 5432;
  }
}

function openRegisterDialog() {
  resetRegisterForm();
  registerDialogVisible.value = true;
}

function validateRegisterForm() {
  if (!registerForm.name.trim()) {
    return "Name is required.";
  }

  if (!registerForm.host.trim()) {
    return "Host is required.";
  }

  if (!registerForm.port || registerForm.port <= 0) {
    return "Port must be greater than 0.";
  }

  if (!registerForm.databaseName.trim()) {
    return "Database name is required.";
  }

  if (!registerForm.engine) {
    return "Engine is required.";
  }

  if (!registerForm.environment.trim()) {
    return "Environment is required.";
  }

  if (!registerForm.username.trim()) {
    return "Username is required.";
  }

  if (!registerForm.password) {
    return "Password is required.";
  }

  return null;
}

function buildCreateRequest(): CreateDatabaseInstanceRequest {
  return {
    name: registerForm.name.trim(),
    host: registerForm.host.trim(),
    port: Number(registerForm.port),
    databaseName: registerForm.databaseName.trim(),
    engine: registerForm.engine,
    environment: registerForm.environment.trim(),
    serviceName: registerForm.serviceName?.trim() || undefined,
    owner: registerForm.owner?.trim() || undefined,
    description: registerForm.description?.trim() || undefined,
    username: registerForm.username.trim(),
    password: registerForm.password,
  };
}

async function submitRegisterDatabase() {
  const validationMessage = validateRegisterForm();

  if (validationMessage) {
    ElMessage.warning(validationMessage);
    return;
  }

  registering.value = true;

  try {
    const createdDatabase = await createDatabaseInstance(buildCreateRequest());

    ElMessage.success(`관리 대상 DB를 추가했습니다. id=${createdDatabase.id}`);

    registerDialogVisible.value = false;

    await loadDatabases();
  } catch (error) {
    if (isDatabaseNotReachableError(error)) {
      ElMessage.error(
        "존재하지 않거나 접근할 수 없는 DB입니다. Host, Port, Database Name, Username, Password를 확인하세요.",
      );
      return;
    }

    ElMessage.error(`DB 등록에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    registering.value = false;
  }
}

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

async function loadDatabases() {
  loading.value = true;
  errorMessage.value = null;

  try {
    databases.value = await getDatabaseInstances();
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error);
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
      `${database.name} Health Check failed. ${getApiErrorMessage(error)}`,
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
      `${database.name} 비활성화에 실패했습니다. ${getApiErrorMessage(error)}`,
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
              이미 존재하는 DB를 관리 대상으로 추가하고 Health 상태를 확인합니다.
            </p>
          </div>

          <div class="card-actions">
            <el-button :icon="Refresh" :loading="loading" @click="loadDatabases">
              Refresh
            </el-button>

            <el-button type="primary" :icon="Plus" @click="openRegisterDialog">
              Add Existing Database
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
        <template #title>Managed Database 등록 기준</template>
        <p>
          DB FleetOps는 이 화면에서 실제 DB를 새로 생성하지 않습니다.
          이미 존재하고 접속 가능한 DB의 접속 정보를 등록한 뒤 Health,
          Diagnostics, Backup, Configuration 작업을 수행합니다.
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
        description="등록된 DB 인스턴스가 없습니다. Add Existing Database 버튼으로 접속 가능한 기존 DB를 추가할 수 있습니다."
      >
        <el-button type="primary" :icon="Connection" @click="openRegisterDialog">
          Add Existing Database
        </el-button>
      </el-empty>
    </el-card>

    <el-dialog
      v-model="registerDialogVisible"
      title="Add Existing Database"
      width="760px"
      destroy-on-close
    >
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        class="page-alert"
      >
        <template #title>기존 DB 등록 안내</template>
        <p>
          이 화면은 실제 데이터베이스를 새로 생성하지 않습니다.
          이미 존재하고 접속 가능한 DB를 DB FleetOps의 관리 대상으로 추가합니다.
        </p>
        <p class="muted-text">
          백엔드가 Docker Compose로 실행 중이면 Host에는 target-mysql을 입력하고,
          로컬 bootRun으로 실행 중이면 localhost 또는 127.0.0.1을 입력합니다.
        </p>
      </el-alert>

      <el-form
        label-position="top"
        class="register-form"
        @submit.prevent
      >
        <div class="form-grid">
          <el-form-item label="Name" required>
            <el-input
              v-model="registerForm.name"
              placeholder="예: target-mysql 또는 order-mysql"
            />
          </el-form-item>

          <el-form-item label="Engine" required>
            <el-select
              v-model="registerForm.engine"
              class="full-width"
              @change="handleEngineChange"
            >
              <el-option label="MYSQL" value="MYSQL" />
              <el-option label="POSTGRESQL" value="POSTGRESQL" disabled />
            </el-select>
          </el-form-item>

          <el-form-item label="Host" required>
            <el-input
              v-model="registerForm.host"
              placeholder="Docker Compose: target-mysql / bootRun: localhost"
            />
          </el-form-item>

          <el-form-item label="Port" required>
            <el-input-number
              v-model="registerForm.port"
              class="full-width"
              :min="1"
              :max="65535"
            />
          </el-form-item>

          <el-form-item label="Database Name" required>
            <el-input
              v-model="registerForm.databaseName"
              placeholder="예: orders"
            />
          </el-form-item>

          <el-form-item label="Environment" required>
            <el-select v-model="registerForm.environment" class="full-width">
              <el-option label="LOCAL" value="LOCAL" />
              <el-option label="DEV" value="DEV" />
              <el-option label="STAGING" value="STAGING" />
              <el-option label="PRODUCTION" value="PRODUCTION" />
            </el-select>
          </el-form-item>

          <el-form-item label="Service Name">
            <el-input
              v-model="registerForm.serviceName"
              placeholder="예: target-mysql 또는 order-service"
            />
          </el-form-item>

          <el-form-item label="Owner">
            <el-input
              v-model="registerForm.owner"
              placeholder="예: platform-team"
            />
          </el-form-item>

          <el-form-item label="Username" required>
            <el-input
              v-model="registerForm.username"
              placeholder="예: root"
              autocomplete="off"
            />
          </el-form-item>

          <el-form-item label="Password" required>
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="target-mysql의 MYSQL_ROOT_PASSWORD 값"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>
        </div>

        <el-form-item label="Description">
          <el-input
            v-model="registerForm.description"
            type="textarea"
            :rows="3"
            placeholder="예: Docker Compose target MySQL managed by DB FleetOps"
          />
        </el-form-item>
      </el-form>

      <div class="registration-help">
        <strong>입력 예시</strong>

        <div class="help-grid">
          <div>
            <span>Docker Compose API 기준</span>
            <code>name = target-mysql</code>
            <code>host = target-mysql</code>
            <code>port = 3306</code>
            <code>databaseName = orders</code>
          </div>

          <div>
            <span>Local bootRun API 기준</span>
            <code>name = target-mysql-local</code>
            <code>host = localhost</code>
            <code>port = 3306</code>
            <code>databaseName = orders</code>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="registerDialogVisible = false">
            Cancel
          </el-button>

          <el-button
            type="primary"
            :loading="registering"
            @click="submitRegisterDatabase"
          >
            Add Managed Target
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>