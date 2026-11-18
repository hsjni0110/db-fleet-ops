<script setup lang="ts">
import FieldHelp from "../components/FieldHelp.vue";
import { operationalLabel } from "../labels";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Back, Refresh } from "@element-plus/icons-vue";

import { getApiErrorMessage, getOperationJob } from "../api";
import OperationJobStatusTag from "../components/OperationJobStatusTag.vue";

import type { OperationJobResponse } from "../types";

const route = useRoute();
const router = useRouter();

const jobId = computed(() => String(route.params.jobId));
const job = ref<OperationJobResponse | null>(null);
const loading = ref(false);
const errorMessage = ref<string | null>(null);

function formatNullable(value?: string | number | null) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return String(value);
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return "-";
  }

  return value.replace("T", " ");
}

async function loadJob() {
  loading.value = true;
  errorMessage.value = null;

  try {
    job.value = await getOperationJob(jobId.value);
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error);
    ElMessage.error("작업 상세 정보를 불러오지 못했습니다.");
  } finally {
    loading.value = false;
  }
}

function goBack() {
  router.push("/jobs");
}

onMounted(() => {
  loadJob();
});
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>운영 작업 상세 #{{ jobId }}</span>
          </div>

          <div class="card-actions">
            <el-button :icon="Back" @click="goBack">작업 목록</el-button>
            <el-button :icon="Refresh" :loading="loading" @click="loadJob">
              새로고침
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
        <template #title>작업 상세 조회 실패</template>
        <p>{{ errorMessage }}</p>
      </el-alert>

      <div v-loading="loading">
        <template v-if="job">
          <div class="detail-summary-grid">
            <div class="summary-tile">
              <span class="summary-label">상태</span>
              <strong>
                <OperationJobStatusTag :status="job.status" />
              </strong>
            </div>

            <div class="summary-tile">
              <span class="summary-label">작업 유형</span>
              <strong>{{ operationalLabel(job.jobType) }}</strong>
            </div>

            <div class="summary-tile">
              <span class="summary-label">대상 데이터베이스</span>
              <strong>#{{ job.targetDatabaseId }}</strong>
            </div>

            <div class="summary-tile">
              <span class="summary-label">요청자</span>
              <strong>{{ job.requestedBy }}</strong>
            </div>
          </div>

          <el-tabs class="detail-tabs">
            <el-tab-pane label="기본 정보">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="작업 번호">
                  {{ job.jobId }}
                </el-descriptions-item>
                <el-descriptions-item label="재시도">
                  {{ job.retryCount }} / {{ job.maxRetryCount }}
                </el-descriptions-item>
                <el-descriptions-item label="등록 시각">
                  {{ formatDateTime(job.createdAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="실행 가능 시각">
                  {{ formatDateTime(job.availableAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="시작 시각">
                  {{ formatDateTime(job.startedAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="종료 시각">
                  {{ formatDateTime(job.finishedAt) }}
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="실행권">
              <div class="tab-toolbar">
                <h3>작업 중복 방지 <FieldHelp label="실행권" text="한 번에 한 실행기만 작업을 처리하도록 제한 시간 동안 부여하는 권한." /></h3>
              </div>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="실행권 보유자">
                  {{ formatNullable(job.leaseOwner) }}
                </el-descriptions-item>
                <el-descriptions-item label="실행권 만료 시각">
                  {{ formatDateTime(job.leaseUntil) }}
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="결과">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="결과 코드">
                  {{ formatNullable(job.resultCode) }}
                </el-descriptions-item>
                <el-descriptions-item label="결과 메시지">
                  <pre class="diagnostic-output">{{ formatNullable(job.resultMessage) }}</pre>
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </template>

        <el-empty
          v-else-if="!loading && !errorMessage"
          description="조회된 작업 없음"
        />
      </div>
    </el-card>
  </section>
</template>
