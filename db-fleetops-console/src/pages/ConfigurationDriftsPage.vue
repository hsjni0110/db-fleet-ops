<script setup lang="ts">
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
    ElMessage.error("Configuration Drift 상세를 불러오지 못했습니다.");
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
            <span>Configuration Drifts</span>
            <p class="card-subtitle">Configuration Check Job이 만든 실제값과 기준값의 비교 결과입니다.</p>
          </div>

          <div class="card-actions">
            <el-button :icon="Search" @click="openOperationJobs">
              Create Check Job
            </el-button>
            <el-button
              :icon="Refresh"
              :loading="loadingLatestDrift || loadingHistory"
              :disabled="!selectedDatabaseId"
              @click="refreshSelectedDatabaseDrifts"
            >
              Refresh
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
        <el-form-item label="Target Database">
          <el-select
            v-model="selectedDatabaseId"
            class="full-width"
            placeholder="DB 선택"
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
        description="No active databases available for drift lookup."
      />
    </el-card>

    <el-card v-if="selectedDatabase" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>Latest Drift</span>
            <p class="card-subtitle">{{ selectedDatabase.name }} 기준 최신 Configuration Drift 결과입니다.</p>
          </div>

          <el-tag type="info">DB #{{ selectedDatabase.id }}</el-tag>
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
          <span class="metric-label">Status</span>
          <el-tag :type="getComplianceTagType(latestDrift.status)" effect="dark">
            {{ latestDrift.status }}
          </el-tag>
          <small>driftId={{ latestDrift.driftId }}</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">Total</span>
          <strong>{{ latestDrift.totalCount }}</strong>
          <small>checked parameters</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">Compliant</span>
          <strong>{{ latestDrift.compliantCount }}</strong>
          <small>expected = actual</small>
        </div>
        <div class="metric-card">
          <span class="metric-label">Non-Compliant / Missing</span>
          <strong>{{ latestDrift.nonCompliantCount }} / {{ latestDrift.missingCount }}</strong>
          <small>{{ latestDrift.checkedAt }}</small>
        </div>
      </div>

      <div v-if="latestDrift" class="detail-summary-grid">
        <div class="summary-tile">
          <span class="summary-label">Profile ID</span>
          <strong>{{ latestDrift.profileId }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Snapshot ID</span>
          <strong>{{ latestDrift.snapshotId }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Engine</span>
          <strong>{{ latestDrift.engineType }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Checked At</span>
          <strong>{{ latestDrift.checkedAt }}</strong>
        </div>
      </div>

      <el-empty
        v-else-if="!loadingLatestDrift && !latestDriftError"
        description="Latest drift result is not available yet."
      />
    </el-card>

    <el-card v-if="selectedDatabase" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>Drift History</span>
            <p class="card-subtitle">History row를 선택하면 Drift Item 상세를 조회합니다.</p>
          </div>

          <el-tag type="info">{{ driftHistory.length }} results</el-tag>
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

      <el-table
        v-loading="loadingHistory"
        class="console-table"
        :data="driftHistory"
        highlight-current-row
        empty-text="No drift history found for this database."
        @row-click="selectDrift"
      >
        <el-table-column prop="driftId" label="Drift ID" width="100" />
        <el-table-column prop="profileId" label="Profile ID" width="110" />
        <el-table-column prop="snapshotId" label="Snapshot ID" width="120" />
        <el-table-column label="Status" width="150">
          <template #default="{ row }">
            <el-tag :type="getComplianceTagType(row.status)" effect="light">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="Total" width="90" />
        <el-table-column prop="compliantCount" label="Compliant" width="120" />
        <el-table-column prop="nonCompliantCount" label="Non-Compliant" width="150" />
        <el-table-column prop="missingCount" label="Missing" width="110" />
        <el-table-column prop="checkedAt" label="Checked At" min-width="190" />
        <el-table-column label="Actions" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              plain
              size="small"
              :icon="View"
              @click.stop="loadDriftDetail(row.driftId)"
            >
              Detail
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="selectedDrift" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>Drift Items #{{ selectedDrift.driftId }}</span>
            <p class="card-subtitle">Expected value와 실제 DB 설정값의 Parameter별 비교 결과입니다.</p>
          </div>

          <el-tag :type="getComplianceTagType(selectedDrift.status)">
            {{ selectedDrift.status }}
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

      <el-table
        v-loading="loadingDriftDetail"
        class="console-table"
        :data="selectedDrift.items"
        empty-text="No drift items found."
      >
        <el-table-column prop="parameterName" label="Parameter" min-width="190" />
        <el-table-column prop="expectedValue" label="Expected" min-width="150" show-overflow-tooltip />
        <el-table-column label="Actual" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatNullable(row.actualValue) }}
          </template>
        </el-table-column>
        <el-table-column prop="valueType" label="Type" width="110" />
        <el-table-column label="Dynamic" width="110">
          <template #default="{ row }">
            <el-tag :type="row.dynamic ? 'success' : 'warning'" effect="light">
              {{ row.dynamic ? "YES" : "NO" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Apply Allowed" width="140">
          <template #default="{ row }">
            <el-tag :type="row.applyAllowed ? 'success' : 'danger'" effect="light">
              {{ row.applyAllowed ? "YES" : "NO" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="150">
          <template #default="{ row }">
            <el-tag :type="getComplianceTagType(row.complianceStatus)" effect="light">
              {{ row.complianceStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="Message" min-width="300" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="Created At" min-width="190" />
      </el-table>
    </el-card>
  </section>
</template>
