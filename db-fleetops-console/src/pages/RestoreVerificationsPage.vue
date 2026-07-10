<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Refresh, Search, View } from "@element-plus/icons-vue";

import {
  getApiErrorMessage,
  getDatabaseInstances,
  getLatestRestoreVerificationByDatabaseId,
  getRestoreVerification,
  getRestoreVerificationByJobId,
} from "../api";

import type {
  DatabaseInstanceSummary,
  RestoreVerificationItemStatus,
  RestoreVerificationResponse,
  RestoreVerificationStatus,
} from "../types";

const router = useRouter();

const databases = ref<DatabaseInstanceSummary[]>([]);
const selectedDatabaseId = ref<number | null>(null);
const selectedVerification = ref<RestoreVerificationResponse | null>(null);
const jobLookupId = ref("");
const verificationLookupId = ref("");

const loadingDatabases = ref(false);
const loadingLatest = ref(false);
const loadingJobLookup = ref(false);
const loadingVerificationLookup = ref(false);
const databasesError = ref<string | null>(null);
const latestError = ref<string | null>(null);
const lookupError = ref<string | null>(null);

const activeDatabases = computed(() => {
  return databases.value.filter((database) => database.status === "ACTIVE");
});

const selectedDatabase = computed(() => {
  return databases.value.find((database) => database.id === selectedDatabaseId.value) ?? null;
});

function getStatusTagType(status: RestoreVerificationStatus) {
  if (status === "VERIFIED") {
    return "success";
  }

  if (status === "FAILED") {
    return "danger";
  }

  if (status === "CLEANUP_FAILED") {
    return "warning";
  }

  return "info";
}

function getItemStatusTagType(status: RestoreVerificationItemStatus) {
  if (status === "VERIFIED") {
    return "success";
  }

  if (status === "MISSING" || status === "COUNT_FAILED") {
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

function isNotFoundError(error: unknown) {
  const message = getApiErrorMessage(error).toLowerCase();

  return message.includes("not found") || message.includes("404");
}

async function loadDatabases() {
  loadingDatabases.value = true;
  databasesError.value = null;

  try {
    databases.value = await getDatabaseInstances();

    if (!selectedDatabaseId.value && activeDatabases.value.length > 0) {
      selectedDatabaseId.value = activeDatabases.value[0].id;
    }
  } catch (error) {
    databasesError.value = getApiErrorMessage(error);
    ElMessage.error("DB 목록을 불러오지 못했습니다.");
  } finally {
    loadingDatabases.value = false;
  }
}

async function loadLatestVerification() {
  if (!selectedDatabaseId.value) {
    selectedVerification.value = null;
    latestError.value = null;
    return;
  }

  loadingLatest.value = true;
  latestError.value = null;
  lookupError.value = null;

  try {
    selectedVerification.value =
      await getLatestRestoreVerificationByDatabaseId(selectedDatabaseId.value);
  } catch (error) {
    selectedVerification.value = null;

    if (!isNotFoundError(error)) {
      latestError.value = getApiErrorMessage(error);
    }
  } finally {
    loadingLatest.value = false;
  }
}

async function lookupByJobId() {
  const normalizedJobId = Number(jobLookupId.value.trim());

  if (!Number.isInteger(normalizedJobId) || normalizedJobId <= 0) {
    ElMessage.warning("조회할 Job ID를 숫자로 입력하세요.");
    return;
  }

  loadingJobLookup.value = true;
  lookupError.value = null;
  latestError.value = null;

  try {
    selectedVerification.value = await getRestoreVerificationByJobId(normalizedJobId);
    selectedDatabaseId.value = selectedVerification.value.databaseId;
    ElMessage.success(`Job #${normalizedJobId} Restore Verification을 조회했습니다.`);
  } catch (error) {
    selectedVerification.value = null;
    lookupError.value = getApiErrorMessage(error);
  } finally {
    loadingJobLookup.value = false;
  }
}

async function lookupByVerificationId() {
  const normalizedVerificationId = Number(verificationLookupId.value.trim());

  if (!Number.isInteger(normalizedVerificationId) || normalizedVerificationId <= 0) {
    ElMessage.warning("조회할 Verification ID를 숫자로 입력하세요.");
    return;
  }

  loadingVerificationLookup.value = true;
  lookupError.value = null;
  latestError.value = null;

  try {
    selectedVerification.value = await getRestoreVerification(normalizedVerificationId);
    selectedDatabaseId.value = selectedVerification.value.databaseId;
    ElMessage.success(`Verification #${normalizedVerificationId} 상세를 조회했습니다.`);
  } catch (error) {
    selectedVerification.value = null;
    lookupError.value = getApiErrorMessage(error);
  } finally {
    loadingVerificationLookup.value = false;
  }
}

function openJobDetail(jobId: number) {
  router.push({ name: "OperationJobDetail", params: { jobId: String(jobId) } });
}

function openDatabaseDetail(databaseId: number) {
  router.push({ name: "DatabaseDetail", params: { databaseId: String(databaseId) } });
}

onMounted(async () => {
  await loadDatabases();

  if (selectedDatabaseId.value) {
    await loadLatestVerification();
  }
});
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>Restore Verifications</span>
            <p class="card-subtitle">Backup Job 이후 생성된 복원 검증 결과를 조회합니다.</p>
          </div>

          <div class="card-actions">
            <el-button
              :icon="Refresh"
              :loading="loadingLatest"
              :disabled="!selectedDatabaseId"
              @click="loadLatestVerification"
            >
              Refresh Latest
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="databasesError"
        class="page-alert"
        type="error"
        :title="databasesError"
        show-icon
        :closable="false"
      />

      <el-form label-position="top" @submit.prevent>
        <div class="detail-summary-grid">
          <el-form-item label="Target Database">
            <el-select
              v-model="selectedDatabaseId"
              class="full-width"
              placeholder="DB 선택"
              :loading="loadingDatabases"
              :disabled="activeDatabases.length === 0"
              @change="loadLatestVerification"
            >
              <el-option
                v-for="database in activeDatabases"
                :key="database.id"
                :label="`${database.name} (#${database.id}, ${database.engine})`"
                :value="database.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="Job ID Lookup">
            <el-input
              v-model="jobLookupId"
              placeholder="operationJobId"
              clearable
              @keyup.enter="lookupByJobId"
            >
              <template #append>
                <el-button
                  :icon="Search"
                  :loading="loadingJobLookup"
                  @click="lookupByJobId"
                />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="Verification ID Lookup">
            <el-input
              v-model="verificationLookupId"
              placeholder="verificationId"
              clearable
              @keyup.enter="lookupByVerificationId"
            >
              <template #append>
                <el-button
                  :icon="View"
                  :loading="loadingVerificationLookup"
                  @click="lookupByVerificationId"
                />
              </template>
            </el-input>
          </el-form-item>

          <div class="summary-tile">
            <span class="summary-label">Selected DB</span>
            <strong>{{ selectedDatabase?.name ?? "-" }}</strong>
            <span class="muted-text">
              {{ selectedDatabase ? `#${selectedDatabase.id} ${selectedDatabase.engine}` : "No active database" }}
            </span>
          </div>
        </div>
      </el-form>

      <el-alert
        v-if="latestError"
        class="page-alert"
        type="warning"
        show-icon
        :closable="false"
      >
        <template #title>Latest Restore Verification 조회 실패</template>
        <p>{{ latestError }}</p>
      </el-alert>

      <el-alert
        v-if="lookupError"
        class="page-alert"
        type="error"
        show-icon
        :closable="false"
      >
        <template #title>Restore Verification 조회 실패</template>
        <p>{{ lookupError }}</p>
      </el-alert>

      <el-empty
        v-if="activeDatabases.length === 0 && !loadingDatabases && !databasesError"
        description="Restore Verification을 조회할 Active DB가 없습니다."
      />
    </el-card>

    <el-card v-if="selectedVerification" shadow="never" v-loading="loadingLatest">
      <template #header>
        <div class="card-header">
          <div>
            <span>Verification #{{ selectedVerification.id }}</span>
            <p class="card-subtitle">
              Job #{{ selectedVerification.operationJobId }} /
              DB #{{ selectedVerification.databaseId }}
            </p>
          </div>

          <div class="card-actions">
            <el-button
              :icon="View"
              @click="openJobDetail(selectedVerification.operationJobId)"
            >
              Open Job
            </el-button>
            <el-button
              :icon="View"
              @click="openDatabaseDetail(selectedVerification.databaseId)"
            >
              Open DB
            </el-button>
          </div>
        </div>
      </template>

      <div class="metric-grid">
        <div class="metric-card">
          <span class="metric-label">Status</span>
          <el-tag :type="getStatusTagType(selectedVerification.status)" effect="dark">
            {{ selectedVerification.status }}
          </el-tag>
          <small>{{ selectedVerification.completedAt ?? selectedVerification.createdAt }}</small>
        </div>

        <div class="metric-card">
          <span class="metric-label">Source DB</span>
          <strong>{{ selectedVerification.sourceDatabaseName }}</strong>
          <small>{{ selectedVerification.temporaryDatabaseName }}</small>
        </div>

        <div class="metric-card">
          <span class="metric-label">Tables</span>
          <strong>
            {{ selectedVerification.checkedTableCount }} /
            {{ selectedVerification.restoredTableCount }}
          </strong>
          <small>checked / restored</small>
        </div>

        <div class="metric-card">
          <span class="metric-label">Total Rows</span>
          <strong>{{ selectedVerification.totalRowCount }}</strong>
          <small>verified rows</small>
        </div>
      </div>

      <div class="detail-summary-grid">
        <div class="summary-tile">
          <span class="summary-label">Operation Job</span>
          <strong>#{{ selectedVerification.operationJobId }}</strong>
          <span class="muted-text">backup workflow</span>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Backup Task</span>
          <strong>#{{ selectedVerification.backupTaskId }}</strong>
          <span class="muted-text">MYSQL_LOGICAL_BACKUP</span>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Restore Verify Task</span>
          <strong>#{{ selectedVerification.restoreVerifyTaskId }}</strong>
          <span class="muted-text">MYSQL_RESTORE_VERIFY</span>
        </div>

        <div class="summary-tile">
          <span class="summary-label">Database</span>
          <strong>#{{ selectedVerification.databaseId }}</strong>
          <span class="muted-text">{{ selectedDatabase?.name ?? "selected verification target" }}</span>
        </div>
      </div>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="Backup File">
          {{ selectedVerification.backupFile }}
        </el-descriptions-item>

        <el-descriptions-item label="Started At">
          {{ formatNullable(selectedVerification.startedAt) }}
        </el-descriptions-item>

        <el-descriptions-item label="Completed At">
          {{ formatNullable(selectedVerification.completedAt) }}
        </el-descriptions-item>

        <el-descriptions-item label="Error">
          {{ formatNullable(selectedVerification.errorCode) }}
          {{ selectedVerification.errorMessage ? `- ${selectedVerification.errorMessage}` : "" }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="selectedVerification" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>Verification Items</span>
            <p class="card-subtitle">복원된 임시 DB에서 테이블 단위로 확인한 결과입니다.</p>
          </div>
        </div>
      </template>

      <el-table
        class="console-table"
        :data="selectedVerification.items"
        border
        stripe
        empty-text="Restore Verification Item이 없습니다."
      >
        <el-table-column prop="tableName" label="Table" min-width="190" />
        <el-table-column prop="existsInRestoredDb" label="Exists" width="110">
          <template #default="{ row }">
            <el-tag :type="row.existsInRestoredDb ? 'success' : 'danger'" effect="light">
              {{ row.existsInRestoredDb ? "YES" : "NO" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rowCount" label="Row Count" width="130">
          <template #default="{ row }">
            {{ formatNullable(row.rowCount) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="Status" width="150">
          <template #default="{ row }">
            <el-tag :type="getItemStatusTagType(row.status)" effect="light">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="Created At" min-width="190" />
        <el-table-column prop="message" label="Message" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatNullable(row.message) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card
      v-else-if="!loadingLatest && !loadingJobLookup && !loadingVerificationLookup"
      shadow="never"
    >
      <el-empty description="선택한 조건의 Restore Verification 결과가 없습니다." />
    </el-card>
  </section>
</template>
