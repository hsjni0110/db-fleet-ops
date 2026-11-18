<script setup lang="ts">
import { operationalLabel } from "../labels";
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Refresh, Search, View } from "@element-plus/icons-vue";

import {
  getApiErrorMessage,
  getConfigurationDrift,
  getConfigurationDriftsByDatabase,
  getDatabaseInstances,
  getLatestConfigurationDrift,
} from "../api";
import type {
  ComplianceStatus,
  ConfigurationDriftResponse,
  DatabaseInstanceSummary,
} from "../types";

const router = useRouter();

const databases = ref<DatabaseInstanceSummary[]>([]);
const selectedDatabaseId = ref<number | null>(null);
const latestDrift = ref<ConfigurationDriftResponse | null>(null);
const driftHistory = ref<ConfigurationDriftResponse[]>([]);
const selectedDrift = ref<ConfigurationDriftResponse | null>(null);

const loadingDatabases = ref(false);
const loadingLatestDrift = ref(false);
const loadingHistory = ref(false);
const loadingDriftDetail = ref(false);
const databasesError = ref<string | null>(null);
const latestDriftError = ref<string | null>(null);
const historyError = ref<string | null>(null);
const driftDetailError = ref<string | null>(null);

const activeDatabases = computed(() => {
  return databases.value.filter((database) => database.status === "ACTIVE");
});

const selectedDatabase = computed(() => {
  return databases.value.find((database) => database.id === selectedDatabaseId.value) ?? null;
});

function getComplianceTagType(status: ComplianceStatus) {
  if (status === "COMPLIANT") {
    return "success";
  }

  if (status === "NON_COMPLIANT") {
    return "warning";
  }

  return "danger";
}

function formatNullable(value?: string | number | null) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return String(value);
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

async function loadLatestDrift() {
  if (!selectedDatabaseId.value) {
    latestDrift.value = null;
    return;
  }

  loadingLatestDrift.value = true;
  latestDriftError.value = null;

  try {
    latestDrift.value = await getLatestConfigurationDrift(selectedDatabaseId.value);
    selectedDrift.value = latestDrift.value;
  } catch (error) {
    latestDrift.value = null;
    latestDriftError.value = getApiErrorMessage(error);
  } finally {
    loadingLatestDrift.value = false;
  }
}

async function loadDriftHistory() {
  if (!selectedDatabaseId.value) {
    driftHistory.value = [];
    return;
  }

  loadingHistory.value = true;
  historyError.value = null;

  try {
    driftHistory.value = await getConfigurationDriftsByDatabase(selectedDatabaseId.value);
  } catch (error) {
    driftHistory.value = [];
    historyError.value = getApiErrorMessage(error);
  } finally {
    loadingHistory.value = false;
  }
}

async function loadDriftDetail(driftId: number) {
  loadingDriftDetail.value = true;
  driftDetailError.value = null;

  try {
    selectedDrift.value = await getConfigurationDrift(driftId);
  } catch (error) {
    driftDetailError.value = getApiErrorMessage(error);
    ElMessage.error("설정 점검 상세를 불러오지 못했습니다.");
  } finally {
    loadingDriftDetail.value = false;
  }
}

async function refreshSelectedDatabaseDrifts() {
  await Promise.all([
    loadLatestDrift(),
    loadDriftHistory(),
  ]);
}

function openOperationJobs() {
  router.push({ name: "OperationJobs" });
}

function selectDrift(row: ConfigurationDriftResponse) {
  loadDriftDetail(row.driftId);
}

watch(
  selectedDatabaseId,
  () => {
    latestDrift.value = null;
    driftHistory.value = [];
    selectedDrift.value = null;
    latestDriftError.value = null;
    historyError.value = null;
    driftDetailError.value = null;

    if (selectedDatabaseId.value) {
      refreshSelectedDatabaseDrifts();
    }
  },
);

onMounted(async () => {
  await loadDatabases();
});
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>설정 차이</span>
          </div>

          <div class="card-actions">
            <el-button :icon="Search" @click="openOperationJobs">
              설정 점검 요청
            </el-button>
            <el-button
              :icon="Refresh"
              :loading="loadingLatestDrift || loadingHistory"
              :disabled="!selectedDatabaseId"
              @click="refreshSelectedDatabaseDrifts"
            >
              새로고침
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
        <el-form-item label="대상 데이터베이스">
          <el-select
            v-model="selectedDatabaseId"
            class="full-width"
            placeholder="데이터베이스 선택"
            :loading="loadingDatabases"
            :disabled="activeDatabases.length === 0"
          >
            <el-option
              v-for="database in activeDatabases"
              :key="database.id"
              :label="`${database.name} (#${database.id}, ${database.engine})`"
              :value="database.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-empty
        v-if="!loadingDatabases && activeDatabases.length === 0"
        description="활성 데이터베이스 없음"
      />
    </el-card>

    <el-card v-if="selectedDatabase" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>최근 설정 점검</span>
          </div>

          <el-tag type="info">데이터베이스 #{{ selectedDatabase.id }}</el-tag>
        </div>
      </template>

      <el-alert
        v-if="latestDriftError"
        class="page-alert"
        type="warning"
        :title="latestDriftError"
        show-icon
        :closable="false"
      />

      <div v-if="latestDrift" v-loading="loadingLatestDrift" class="metric-grid">
        <div class="metric-card">
          <span class="metric-label">상태</span>
          <el-tag :type="getComplianceTagType(latestDrift.status)" effect="dark">
            {{ operationalLabel(latestDrift.status) }}
          </el-tag>
          <small>점검 #{{ latestDrift.driftId }}</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">점검 항목</span>
          <strong>{{ latestDrift.totalCount }}</strong>
          <small>점검 항목</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">기준 일치</span>
          <strong>{{ latestDrift.compliantCount }}</strong>

        </div>
        <div class="metric-card">
          <span class="metric-label">불일치 / 누락</span>
          <strong>{{ latestDrift.nonCompliantCount }} / {{ latestDrift.missingCount }}</strong>
          <small>{{ latestDrift.checkedAt }}</small>
        </div>
      </div>

      <div v-if="latestDrift" class="detail-summary-grid">
        <div class="summary-tile">
          <span class="summary-label">기준 번호</span>
          <strong>{{ latestDrift.profileId }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">설정 기록 번호</span>
          <strong>{{ latestDrift.snapshotId }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">엔진</span>
          <strong>{{ operationalLabel(latestDrift.engineType) }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">점검 시각</span>
          <strong>{{ latestDrift.checkedAt }}</strong>
        </div>
      </div>

      <el-empty
        v-else-if="!loadingLatestDrift && !latestDriftError"
        description="설정 점검 결과 없음"
      />
    </el-card>

    <el-card v-if="selectedDatabase" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>설정 점검 이력</span>
          </div>

          <el-tag type="info">{{ driftHistory.length }} 건</el-tag>
        </div>
      </template>

      <el-alert
        v-if="historyError"
        class="page-alert"
        type="warning"
        :title="historyError"
        show-icon
        :closable="false"
      />

      <el-table scrollbar-tabindex="0"
        v-loading="loadingHistory"
        class="console-table"
        :data="driftHistory"
        highlight-current-row
        empty-text="설정 점검 이력 없음"
        @row-click="selectDrift"
      >
        <el-table-column prop="driftId" label="점검 번호" width="100" />
        <el-table-column prop="profileId" label="기준 번호" width="110" />
        <el-table-column prop="snapshotId" label="설정 기록 번호" width="120" />
        <el-table-column label="상태" width="150">
          <template #default="{ row }">
            <el-tag :type="getComplianceTagType(row.status)" effect="light">
              {{ operationalLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="점검 항목" width="90" />
        <el-table-column prop="compliantCount" label="기준 일치" width="120" />
        <el-table-column prop="nonCompliantCount" label="불일치" width="150" />
        <el-table-column prop="missingCount" label="누락" width="110" />
        <el-table-column prop="checkedAt" label="점검 시각" min-width="190" />
        <el-table-column label="관리" width="120">
          <template #default="{ row }">
            <el-button
              type="primary"
              plain
              size="small"
              :icon="View"
              @click.stop="loadDriftDetail(row.driftId)"
            >
              상세
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="selectedDrift" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>항목별 설정 차이 #{{ selectedDrift.driftId }}</span>
          </div>

          <el-tag :type="getComplianceTagType(selectedDrift.status)">
            {{ operationalLabel(selectedDrift.status) }}
          </el-tag>
        </div>
      </template>

      <el-alert
        v-if="driftDetailError"
        class="page-alert"
        type="error"
        :title="driftDetailError"
        show-icon
        :closable="false"
      />

      <el-table scrollbar-tabindex="0"
        v-loading="loadingDriftDetail"
        class="console-table"
        :data="selectedDrift.items"
        empty-text="항목별 점검 결과 없음"
      >
        <el-table-column prop="parameterName" label="설정 항목" min-width="190" />
        <el-table-column prop="expectedValue" label="기준값" min-width="150" show-overflow-tooltip />
        <el-table-column label="현재값" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatNullable(row.actualValue) }}
          </template>
        </el-table-column>
        <el-table-column prop="valueType" label="유형" width="110">
          <template #default="{ row }">{{ operationalLabel(row.valueType) }}</template>
        </el-table-column>
        <el-table-column label="재시작 없이 변경" width="110">
          <template #default="{ row }">
            <el-tag :type="row.dynamic ? 'success' : 'warning'" effect="light">
              {{ row.dynamic ? "가능" : "불가" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="변경 허용" width="140">
          <template #default="{ row }">
            <el-tag :type="row.applyAllowed ? 'success' : 'danger'" effect="light">
              {{ row.applyAllowed ? "허용" : "제한" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="상태" width="150">
          <template #default="{ row }">
            <el-tag :type="getComplianceTagType(row.complianceStatus)" effect="light">
              {{ operationalLabel(row.complianceStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="메시지" min-width="300" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="등록 시각" min-width="190" />
      </el-table>
    </el-card>
  </section>
</template>
