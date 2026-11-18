<script setup lang="ts">
import FieldHelp from "../components/FieldHelp.vue";
import { operationalLabel } from "../labels";
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  CircleCheckFilled,
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
  getBusinessVerificationProfiles,
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
  BusinessVerificationProfileResponse,
} from "../types";

const router = useRouter();

const databases = ref<DatabaseInstanceSummary[]>([]);
const loading = ref(false);
const errorMessage = ref<string | null>(null);
const healthCheckLoadingId = ref<number | null>(null);
const lastHealthResults = ref<Record<number, InventoryHealthCheckResponse>>({});
const businessProfiles = ref<BusinessVerificationProfileResponse[]>([]);

const registerDialogVisible = ref(false);
const registering = ref(false);

const registerForm = reactive<CreateDatabaseInstanceRequest>({
  name: "",
  host: "target-mysql",
  port: 3306,
  databaseName: "insurance_sandbox",
  engine: "MYSQL",
  environment: "LOCAL",
  serviceName: "target-mysql",
  owner: "platform-team",
  description: "",
  username: "root",
  password: "",
  businessVerificationProfileKey: "",
});

function resetRegisterForm() {
  registerForm.name = "";
  registerForm.host = "target-mysql";
  registerForm.port = registerForm.engine === "POSTGRESQL" ? 5432 : 3306;
  registerForm.databaseName = "insurance_sandbox";
  registerForm.environment = "LOCAL";
  registerForm.serviceName = "target-mysql";
  registerForm.owner = "platform-team";
  registerForm.description = "";
  registerForm.username = "root";
  registerForm.password = "";
  registerForm.businessVerificationProfileKey = "";
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
    return "이름을 입력하세요.";
  }

  if (!registerForm.host.trim()) {
    return "호스트 주소를 입력하세요.";
  }

  if (!registerForm.port || registerForm.port <= 0) {
    return "포트 번호는 1 이상이어야 합니다.";
  }

  if (!registerForm.databaseName.trim()) {
    return "데이터베이스 이름을 입력하세요.";
  }

  if (!registerForm.engine) {
    return "엔진을 선택하세요.";
  }

  if (!registerForm.environment.trim()) {
    return "환경을 선택하세요.";
  }

  if (!registerForm.username.trim()) {
    return "접속 사용자를 입력하세요.";
  }

  if (!registerForm.password) {
    return "접속 비밀번호를 입력하세요.";
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
    businessVerificationProfileKey: registerForm.businessVerificationProfileKey || null,
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
      `${database.name} 연결 점검 완료 · ${result.status}`,
    );
  } catch (error) {
    ElMessage.error(
      `${database.name} 연결 점검 실패. ${getApiErrorMessage(error)}`,
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
      `${database.name} 데이터베이스를 비활성화하시겠습니까? 삭제가 아니라 INACTIVE 상태로 변경됩니다.`,
      "DB 비활성화 확인",
      {
        confirmButtonText: "비활성화",
        cancelButtonText: "취소",
        type: "warning",
      },
    );

    await deactivateDatabaseInstance(database.id);

    ElMessage.success(`${database.name} 데이터베이스를 비활성화했습니다.`);

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
  getBusinessVerificationProfiles()
    .then((profiles) => { businessProfiles.value = profiles; })
    .catch(() => { ElMessage.warning("업무 검증 기준 목록을 불러오지 못했습니다."); });
});
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>데이터베이스 목록</span>
          </div>

          <div class="card-actions">
            <el-button :icon="Refresh" :loading="loading" @click="loadDatabases">
              새로고침
            </el-button>

            <el-button type="primary" :icon="Plus" @click="openRegisterDialog">
              기존 데이터베이스 등록
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
        <template #title>데이터베이스 목록 조회 실패</template>
        <p>{{ errorMessage }}</p>
      </el-alert>

      <el-table scrollbar-tabindex="0"
        v-loading="loading"
        :data="databases"
        border
        stripe
        class="console-table"
        empty-text="등록된 데이터베이스 없음"
      >
        <el-table-column prop="id" label="번호" width="90" />

        <el-table-column prop="name" label="이름" min-width="180">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.name }}</strong>

            </div>
          </template>
        </el-table-column>

        <el-table-column prop="engine" label="엔진" width="140">
          <template #default="{ row }">
            <el-tag :type="getEngineTagType(row.engine)" effect="light">
              {{ operationalLabel(row.engine) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="등록 상태" width="160">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" effect="light">
              {{ operationalLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="최근 연결 점검" min-width="220">
          <template #default="{ row }">
            <div
              v-if="lastHealthResults[row.id]"
              class="health-result-cell"
            >
              <el-tag
                :type="getHealthTagType(lastHealthResults[row.id].status)"
                effect="dark"
              >
                {{ operationalLabel(lastHealthResults[row.id].status) }}
              </el-tag>

              <span>
                {{ lastHealthResults[row.id].responseTimeMs }}ms
              </span>
            </div>

            <span v-else class="muted-text">미점검</span>
          </template>
        </el-table-column>

        <el-table-column label="메시지" min-width="260">
          <template #default="{ row }">
            <span v-if="lastHealthResults[row.id]">
              {{ lastHealthResults[row.id].message }}
            </span>

            <span v-else class="muted-text">
              연결 점검 필요
            </span>
          </template>
        </el-table-column>

        <el-table-column label="관리" width="320">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                size="small"
                :icon="View"
                @click="handleViewDetail(row)"
              >
                상세
              </el-button>

              <el-button
                size="small"
                :icon="CircleCheckFilled"
                :loading="healthCheckLoadingId === row.id"
                @click="handleHealthCheck(row)"
              >
                연결 점검
              </el-button>

              <el-button
                size="small"
                type="danger"
                plain
                :icon="Delete"
                :disabled="row.status === 'INACTIVE'"
                @click="handleDeactivate(row)"
              >
                비활성화
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

    </el-card>

    <el-dialog
      v-model="registerDialogVisible"
      width="720px"
      class="database-register-dialog"
      destroy-on-close
    >
      <template #header="{ titleId, titleClass }">
        <span :id="titleId" :class="titleClass">기존 데이터베이스 등록</span>
        <FieldHelp label="기존 데이터베이스 등록" text="이미 실행 중인 데이터베이스의 접속 정보 등록. 새 데이터베이스 생성 없음." />
      </template>
      <div class="dialog-scroll-body">

        <el-form
          label-position="top"
          class="register-form"
          @submit.prevent
        >
          <div class="form-grid">
            <el-form-item label="이름" required>
              <el-input
                v-model="registerForm.name"
                placeholder="예: target-mysql 또는 order-mysql"
              />
            </el-form-item>

            <el-form-item label="엔진" required>
              <el-select
                v-model="registerForm.engine"
                class="full-width"
                @change="handleEngineChange"
              >
                <el-option label="MySQL" value="MYSQL" />
                <el-option label="PostgreSQL" value="POSTGRESQL" disabled />
              </el-select>
            </el-form-item>

            <el-form-item label="호스트" required>
              <el-input
                v-model="registerForm.host"
                placeholder="예: target-mysql, localhost, 127.0.0.1"
              />
            </el-form-item>

            <el-form-item label="포트" required>
              <el-input-number
                v-model="registerForm.port"
                class="full-width"
                :min="1"
                :max="65535"
              />
            </el-form-item>

            <el-form-item label="데이터베이스 이름" required>
              <el-input
                v-model="registerForm.databaseName"
                placeholder="예: orders"
              />
            </el-form-item>

            <el-form-item label="환경" required>
              <el-select v-model="registerForm.environment" class="full-width">
                <el-option :label="operationalLabel('LOCAL')" value="LOCAL" />
                <el-option :label="operationalLabel('DEV')" value="DEV" />
                <el-option :label="operationalLabel('STAGING')" value="STAGING" />
                <el-option :label="operationalLabel('PRODUCTION')" value="PRODUCTION" />
              </el-select>
            </el-form-item>

            <el-form-item label="업무 검증 기준">
              <el-select v-model="registerForm.businessVerificationProfileKey" :empty-values="[null, undefined]" class="full-width">
                <el-option label="기본 복원 검증만 적용" value="" />
                <el-option
                  v-for="profile in businessProfiles"
                  :key="profile.key"
                  :label="`${profile.name} (규칙 ${profile.ruleCount}개)`"
                  :value="profile.key"
                  :disabled="profile.engine !== registerForm.engine"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="서비스 이름">
              <el-input
                v-model="registerForm.serviceName"
                placeholder="예: order-service"
              />
            </el-form-item>

            <el-form-item label="담당자">
              <el-input
                v-model="registerForm.owner"
                placeholder="예: platform-team"
              />
            </el-form-item>

            <el-form-item label="접속 사용자" required>
              <el-input
                v-model="registerForm.username"
                placeholder="예: root"
                autocomplete="off"
              />
            </el-form-item>

            <el-form-item label="접속 비밀번호" required>
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="DB 접속 비밀번호"
                show-password
                autocomplete="new-password"
              />
            </el-form-item>
          </div>

          <el-form-item label="설명">
            <el-input
              v-model="registerForm.description"
              type="textarea"
              :rows="3"
              placeholder="예: 주문 서비스 운영 DB"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="registerDialogVisible = false">
            취소
          </el-button>

          <el-button
            type="primary"
            :loading="registering"
            @click="submitRegisterDatabase"
          >
            등록
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>
