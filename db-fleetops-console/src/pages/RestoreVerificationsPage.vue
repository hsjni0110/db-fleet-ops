<script setup lang="ts">
import FieldHelp from "../components/FieldHelp.vue";
import { operationalLabel } from "../labels";
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

function getPhaseStatusTagType(status?: string) {
  if (status === "VERIFIED" || status === "COMPLETED") return "success";
  if (status === "FAILED" || status === "ERROR") return "danger";
  if (status === "SKIPPED") return "warning";
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
    ElMessage.warning("조회할 작업 번호를 숫자로 입력하세요.");
    return;
  }

  loadingJobLookup.value = true;
  lookupError.value = null;
  latestError.value = null;

  try {
    selectedVerification.value = await getRestoreVerificationByJobId(normalizedJobId);
    selectedDatabaseId.value = selectedVerification.value.databaseId;
    ElMessage.success(`작업 #${normalizedJobId} 복원 검증을 조회했습니다.`);
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
    ElMessage.warning("조회할 검증 번호를 숫자로 입력하세요.");
    return;
  }

  loadingVerificationLookup.value = true;
  lookupError.value = null;
  latestError.value = null;

  try {
    selectedVerification.value = await getRestoreVerification(normalizedVerificationId);
    selectedDatabaseId.value = selectedVerification.value.databaseId;
    ElMessage.success(`복원 검증 #${normalizedVerificationId} 상세를 조회했습니다.`);
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
            <span>검증 결과 조회</span>
          </div>

          <div class="card-actions">
            <el-button
              :icon="Refresh"
              :loading="loadingLatest"
              :disabled="!selectedDatabaseId"
              @click="loadLatestVerification"
            >
              최근 결과 새로고침
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
          <el-form-item label="대상 데이터베이스">
            <el-select
              v-model="selectedDatabaseId"
              class="full-width"
              placeholder="데이터베이스 선택"
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

          <el-form-item label="작업 번호">
            <el-input
              v-model="jobLookupId"
              placeholder="예: 15"
              clearable
              @keyup.enter="lookupByJobId"
            >
              <template #append>
                <el-button
                  aria-label="작업 번호 조회"
                  :icon="Search"
                  :loading="loadingJobLookup"
                  @click="lookupByJobId"
                />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="검증 번호">
            <el-input
              v-model="verificationLookupId"
              placeholder="예: 3"
              clearable
              @keyup.enter="lookupByVerificationId"
            >
              <template #append>
                <el-button
                  aria-label="검증 번호 조회"
                  :icon="View"
                  :loading="loadingVerificationLookup"
                  @click="lookupByVerificationId"
                />
              </template>
            </el-input>
          </el-form-item>

        </div>
      </el-form>

      <el-alert
        v-if="latestError"
        class="page-alert"
        type="warning"
        show-icon
        :closable="false"
      >
        <template #title>최근 복원 검증 조회 실패</template>
        <p>{{ latestError }}</p>
      </el-alert>

      <el-alert
        v-if="lookupError"
        class="page-alert"
        type="error"
        show-icon
        :closable="false"
      >
        <template #title>복원 검증 조회 실패</template>
        <p>{{ lookupError }}</p>
      </el-alert>

      <el-empty
        v-if="activeDatabases.length === 0 && !loadingDatabases && !databasesError"
        description="활성 데이터베이스 없음"
      />
    </el-card>

    <el-card v-if="selectedVerification" shadow="never" v-loading="loadingLatest">
      <template #header>
        <div class="card-header">
          <div>
            <span>복원 검증 #{{ selectedVerification.id }}</span>
            <span class="record-context">
              작업 #{{ selectedVerification.operationJobId }} /
              DB #{{ selectedVerification.databaseId }}
            </span>
          </div>

          <div class="card-actions">
            <el-button
              :icon="View"
              @click="openJobDetail(selectedVerification.operationJobId)"
            >
              작업 상세
            </el-button>
            <el-button
              :icon="View"
              @click="openDatabaseDetail(selectedVerification.databaseId)"
            >
              데이터베이스 상세
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="selectedVerification.errorCode === 'BACKUP_INTEGRITY_CHECK_FAILED'"
        class="page-alert"
        title="백업 파일 무결성 확인 실패"
        description="백업 당시와 파일 내용 대조 실패. 임시 데이터베이스 생성 전 복원 중단."
        type="error"
        show-icon
        :closable="false"
      />
      <p v-if="selectedVerification.errorCode" class="muted-text">오류 코드 · <code>{{ selectedVerification.errorCode }}</code></p>
      <div class="metric-grid">
        <div class="metric-card">
          <span class="metric-label">상태</span>
          <el-tag :type="getStatusTagType(selectedVerification.status)" effect="dark">
            {{ operationalLabel(selectedVerification.status) }}
          </el-tag>
          <small>{{ selectedVerification.completedAt ?? selectedVerification.createdAt }}</small>
        </div>

        <div class="metric-card">
          <span class="metric-label">원본 데이터베이스</span>
          <strong>{{ selectedVerification.sourceDatabaseName }}</strong>
          <small>{{ selectedVerification.temporaryDatabaseName }}</small>
        </div>

        <div class="metric-card">
          <span class="metric-label">검증 / 복원 테이블 수</span>
          <strong>
            {{ selectedVerification.checkedTableCount }} /
            {{ selectedVerification.restoredTableCount }}
          </strong>

        </div>

        <div class="metric-card">
          <span class="metric-label">검증 행 수</span>
          <strong>{{ selectedVerification.totalRowCount }}</strong>

        </div>
      </div>

      <div
        v-if="selectedVerification.technicalStatus || selectedVerification.businessVerification || selectedVerification.cleanupStatus"
        class="metric-grid"
      >
        <div v-if="selectedVerification.technicalStatus" class="metric-card">
          <span class="metric-label">
            기본 복원 검증
            <FieldHelp label="기본 복원 검증" text="백업을 임시 데이터베이스에 복원한 후 필수 테이블과 설정된 행 수 조회 확인." />
          </span>
          <el-tag :type="getPhaseStatusTagType(selectedVerification.technicalStatus)" effect="light">
            {{ operationalLabel(selectedVerification.technicalStatus) }}
          </el-tag>
        </div>
        <div v-if="selectedVerification.businessVerification" class="metric-card">
          <span class="metric-label">
            업무 검증
            <FieldHelp label="업무 검증" text="복원된 데이터에서 누락된 계약이나 중복 지급 등의 업무 규칙 위반 확인." />
          </span>
          <el-tag :type="getPhaseStatusTagType(selectedVerification.businessVerification.status)" effect="light">
            {{ operationalLabel(selectedVerification.businessVerification.status) }}
          </el-tag>
          <small>{{ selectedVerification.businessVerification.profileKey ?? "기준 없음" }}</small>
        </div>
        <div v-if="selectedVerification.businessVerification" class="metric-card">
          <span class="metric-label">검사한 업무 규칙</span>
          <strong>{{ selectedVerification.businessVerification.checkedRuleCount }}</strong>
          <small>{{ selectedVerification.businessVerification.failedRuleCount }} 개 실패</small>
        </div>
        <div v-if="selectedVerification.cleanupStatus" class="metric-card">
          <span class="metric-label">
            임시 데이터베이스 정리
            <FieldHelp label="임시 데이터베이스 정리" text="복원 검증에 사용한 임시 데이터베이스 삭제 결과." />
          </span>
          <el-tag :type="getPhaseStatusTagType(selectedVerification.cleanupStatus)" effect="light">
            {{ operationalLabel(selectedVerification.cleanupStatus) }}
          </el-tag>
        </div>
      </div>

      <details class="record-details">
        <summary>백업 파일 및 실행 기록</summary>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="백업 단계 번호">#{{ selectedVerification.backupTaskId }}</el-descriptions-item>
          <el-descriptions-item label="복원 단계 번호">#{{ selectedVerification.restoreVerifyTaskId }}</el-descriptions-item>
          <el-descriptions-item label="백업 파일">
            {{ selectedVerification.backupFile }}
          </el-descriptions-item>

          <el-descriptions-item label="시작 시각">
            {{ formatNullable(selectedVerification.startedAt) }}
          </el-descriptions-item>

          <el-descriptions-item label="완료 시각">
            {{ formatNullable(selectedVerification.completedAt) }}
          </el-descriptions-item>

          <el-descriptions-item label="오류">
            {{ formatNullable(selectedVerification.errorCode) }}
            {{ selectedVerification.errorMessage ? `- ${selectedVerification.errorMessage}` : "" }}
          </el-descriptions-item>
        </el-descriptions>
      </details>
    </el-card>

    <el-card v-if="selectedVerification?.businessVerification" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>업무 규칙 검증 결과</span>
          </div>
        </div>
      </template>
      <el-table scrollbar-tabindex="0"
        :data="selectedVerification.businessVerification.ruleResults"
        border
        stripe
        empty-text="업무 규칙 결과 없음"
      >
        <el-table-column prop="ruleCode" label="규칙 코드" min-width="260" />
        <el-table-column prop="ruleName" label="이름" min-width="220" />
        <el-table-column prop="status" label="상태" width="120">
          <template #default="{ row }">
            <el-tag :type="getPhaseStatusTagType(row.status)" effect="light">{{ operationalLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="violationCount" label="위반 건수" width="120">
          <template #default="{ row }">{{ formatNullable(row.violationCount) }}</template>
        </el-table-column>
        <el-table-column prop="durationMillis" label="실행 시간 (ms)" width="140" />
        <el-table-column prop="message" label="메시지" min-width="280" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="selectedVerification" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>테이블별 복원 결과</span>
          </div>
        </div>
      </template>

      <el-table scrollbar-tabindex="0"
        class="console-table"
        :data="selectedVerification.items"
        border
        stripe
        empty-text="테이블별 결과 없음"
      >
        <el-table-column prop="tableName" label="테이블" min-width="190" />
        <el-table-column prop="existsInRestoredDb" label="존재 여부" width="110">
          <template #default="{ row }">
            <el-tag :type="row.existsInRestoredDb ? 'success' : 'danger'" effect="light">
              {{ row.existsInRestoredDb ? "있음" : "없음" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rowCount" label="행 수" width="130">
          <template #default="{ row }">
            {{ formatNullable(row.rowCount) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="상태" width="150">
          <template #default="{ row }">
            <el-tag :type="getItemStatusTagType(row.status)" effect="light">
              {{ operationalLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="등록 시각" min-width="190" />
        <el-table-column prop="message" label="메시지" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatNullable(row.message) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card
      v-else-if="activeDatabases.length && !loadingLatest && !loadingJobLookup && !loadingVerificationLookup && !latestError && !lookupError && !databasesError"
      shadow="never"
    >
      <el-empty description="조회된 복원 검증 결과 없음" />
    </el-card>
  </section>
</template>
