<script setup lang="ts">
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
    ElMessage.error("Job 상세 정보를 불러오지 못했습니다.");
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
            <span>Operation Job Detail #{{ jobId }}</span>
            <p class="card-subtitle">
              비동기 운영 Job의 상태, lease, retry, 결과 메시지를 확인합니다.
            </p>
          </div>

          <div class="card-actions">
            <el-button :icon="Back" @click="goBack">Jobs</el-button>
            <el-button :icon="Refresh" :loading="loading" @click="loadJob">
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
        <template #title>Operation Job API 호출 실패</template>
        <p>{{ errorMessage }}</p>
      </el-alert>

      <div v-loading="loading">
        <template v-if="job">
          <div class="detail-summary-grid">
            <div class="summary-tile">
              <span class="summary-label">Status</span>
              <strong>
                <OperationJobStatusTag :status="job.status" />
              </strong>
            </div>

            <div class="summary-tile">
              <span class="summary-label">Job Type</span>
              <strong>{{ job.jobType }}</strong>
            </div>

            <div class="summary-tile">
              <span class="summary-label">Target Database</span>
              <strong>#{{ job.targetDatabaseId }}</strong>
            </div>

            <div class="summary-tile">
              <span class="summary-label">Requested By</span>
              <strong>{{ job.requestedBy }}</strong>
            </div>
          </div>

          <el-tabs class="detail-tabs">
            <el-tab-pane label="Overview">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="Job ID">
                  {{ job.jobId }}
                </el-descriptions-item>
                <el-descriptions-item label="Retry">
                  {{ job.retryCount }} / {{ job.maxRetryCount }}
                </el-descriptions-item>
                <el-descriptions-item label="Created At">
                  {{ formatDateTime(job.createdAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="Available At">
                  {{ formatDateTime(job.availableAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="Started At">
                  {{ formatDateTime(job.startedAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="Finished At">
                  {{ formatDateTime(job.finishedAt) }}
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="Lease">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="Lease Owner">
                  {{ formatNullable(job.leaseOwner) }}
                </el-descriptions-item>
                <el-descriptions-item label="Lease Until">
                  {{ formatDateTime(job.leaseUntil) }}
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="Result">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="Result Code">
                  {{ formatNullable(job.resultCode) }}
                </el-descriptions-item>
                <el-descriptions-item label="Result Message">
                  {{ formatNullable(job.resultMessage) }}
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </template>

        <el-empty
          v-else-if="!loading"
          description="조회된 Operation Job이 없습니다."
        />
      </div>
    </el-card>
  </section>
</template>
