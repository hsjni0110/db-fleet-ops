<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import { operationalLabel } from "../labels";

import {
  createBackupJob,
  createConfigurationApplyJob,
  createConfigurationCheckJob,
  createIdempotencyKey,
  getApiErrorMessage,
  getConfigurationProfiles,
  getDatabaseInstances,
  getOperationJob,
} from "../api";
import OperationJobControls from "../components/OperationJobControls.vue";
import OperationJobPageAlerts from "../components/OperationJobPageAlerts.vue";
import OperationJobTable from "../components/OperationJobTable.vue";

import type {
  BackupJobRequest,
  ConfigurationProfileResponse,
  CreateConfigurationApplyJobRequest,
  CreateConfigurationCheckJobRequest,
  DatabaseInstanceSummary,
  OperationJobResponse,
  OperationJobType,
} from "../types";

const router = useRouter();

const databases = ref<DatabaseInstanceSummary[]>([]);
const profiles = ref<ConfigurationProfileResponse[]>([]);
const jobs = ref<OperationJobResponse[]>([]);
const loadingDatabases = ref(false);
const loadingProfiles = ref(false);
const creatingJob = ref(false);
const lookingUpJob = ref(false);
const databasesError = ref<string | null>(null);
const profilesError = ref<string | null>(null);

const activeDatabases = computed(() => {
  return databases.value.filter((database) => database.status === "ACTIVE");
});

function upsertJob(job: OperationJobResponse) {
  const existingIndex = jobs.value.findIndex((item) => item.jobId === job.jobId);

  if (existingIndex >= 0) {
    jobs.value.splice(existingIndex, 1, job);
    return;
  }

  jobs.value = [job, ...jobs.value];
}

function validateBackupRequest(
  databaseId: number | null,
  request: BackupJobRequest,
) {
  if (!databaseId) {
    return "백업 대상 DB를 선택하세요.";
  }

  if (!request.reason.trim()) {
    return "백업 사유를 입력하세요.";
  }

  if (!request.requestedBy.trim()) {
    return "요청자를 입력하세요.";
  }

  return null;
}

function validateConfigurationRequest(databaseId: number | null, profileId: number | null, requestedBy: string) {
  if (!databaseId) {
    return "작업 대상 DB를 선택하세요.";
  }

  if (!profileId) {
    return "설정 기준을 선택하세요.";
  }

  if (!requestedBy.trim()) {
    return "요청자를 입력하세요.";
  }

  return null;
}

async function loadDatabases() {
  loadingDatabases.value = true;
  databasesError.value = null;

  try {
    databases.value = await getDatabaseInstances();
  } catch (error) {
    databasesError.value = getApiErrorMessage(error);
    ElMessage.error("DB 목록을 불러오지 못했습니다.");
  } finally {
    loadingDatabases.value = false;
  }
}

async function loadProfiles() {
  loadingProfiles.value = true;
  profilesError.value = null;

  try {
    profiles.value = await getConfigurationProfiles();
  } catch (error) {
    profilesError.value = getApiErrorMessage(error);
    ElMessage.error("설정 기준 목록을 불러오지 못했습니다.");
  } finally {
    loadingProfiles.value = false;
  }
}

async function submitOperationJob(
  jobType: OperationJobType,
  databaseId: number | null,
  request: BackupJobRequest | CreateConfigurationCheckJobRequest | CreateConfigurationApplyJobRequest,
) {
  if (jobType === "RESTART") {
    ElMessage.warning("재시작 요청 미지원");
    return;
  }

  const validationMessage =
    jobType === "BACKUP"
      ? validateBackupRequest(databaseId, request as BackupJobRequest)
      : validateConfigurationRequest(
          databaseId,
          (request as CreateConfigurationCheckJobRequest).profileId,
          request.requestedBy,
        );

  if (validationMessage) {
    ElMessage.warning(validationMessage);
    return;
  }

  if (
    jobType === "CONFIGURATION_APPLY" &&
    (request as CreateConfigurationApplyJobRequest).parameters.length === 0
  ) {
    ElMessage.warning("항목=값 형식의 설정을 한 줄 이상 입력하세요.");
    return;
  }

  creatingJob.value = true;

  try {
    let createdJob: OperationJobResponse;

    if (jobType === "BACKUP") {
      createdJob = await createBackupJob(
        Number(databaseId),
        {
          reason: (request.reason ?? "").trim(),
          requestedBy: request.requestedBy.trim(),
        },
        createIdempotencyKey("backup"),
      );
    } else if (jobType === "CONFIGURATION_CHECK") {
      const configurationRequest = request as CreateConfigurationCheckJobRequest;

      createdJob = await createConfigurationCheckJob(
        Number(databaseId),
        {
          profileId: Number(configurationRequest.profileId),
          reason: configurationRequest.reason?.trim() || undefined,
          requestedBy: configurationRequest.requestedBy.trim(),
        },
        createIdempotencyKey("config-check"),
      );
    } else {
      const configurationRequest = request as CreateConfigurationApplyJobRequest;

      createdJob = await createConfigurationApplyJob(
        Number(databaseId),
        {
          profileId: Number(configurationRequest.profileId),
          reason: configurationRequest.reason?.trim() || undefined,
          requestedBy: configurationRequest.requestedBy.trim(),
          parameters: configurationRequest.parameters,
        },
        createIdempotencyKey("config-apply"),
      );
    }

    upsertJob(createdJob);
    ElMessage.success(`${operationalLabel(createdJob.jobType)} 요청 완료 · 작업 #${createdJob.jobId}`);
  } catch (error) {
    ElMessage.error(`운영 작업 생성에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    creatingJob.value = false;
  }
}

async function lookupJob(jobId: string) {
  const normalizedJobId = jobId.trim();

  if (!normalizedJobId) {
    ElMessage.warning("조회할 작업 번호를 입력하세요.");
    return;
  }

  lookingUpJob.value = true;

  try {
    const job = await getOperationJob(normalizedJobId);

    upsertJob(job);
    ElMessage.success(`작업 #${job.jobId} 상태를 조회했습니다.`);
  } catch (error) {
    ElMessage.error(`작업 조회에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    lookingUpJob.value = false;
  }
}

async function refreshJob(job: OperationJobResponse) {
  try {
    const refreshedJob = await getOperationJob(job.jobId);

    upsertJob(refreshedJob);
    ElMessage.success(`작업 #${job.jobId} 상태를 갱신했습니다.`);
  } catch (error) {
    ElMessage.error(`작업 새로고침에 실패했습니다. ${getApiErrorMessage(error)}`);
  }
}

function viewJobDetail(job: OperationJobResponse) {
  router.push(`/jobs/${job.jobId}`);
}

onMounted(() => {
  loadDatabases();
  loadProfiles();
});
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>작업 요청 및 조회</span>
          </div>

          <div class="card-actions">
            <el-button
              :icon="Refresh"
              :loading="loadingDatabases"
              @click="loadDatabases"
            >
              데이터베이스 새로고침
            </el-button>
          </div>
        </div>
      </template>

      <OperationJobPageAlerts
        :databases-error="databasesError"
        :profiles-error="profilesError"
      />

      <OperationJobControls
        :active-databases="activeDatabases"
        :profiles="profiles"
        :loading-databases="loadingDatabases"
        :loading-profiles="loadingProfiles"
        :creating-job="creatingJob"
        :looking-up-job="lookingUpJob"
        @create-operation-job="submitOperationJob"
        @lookup-job="lookupJob"
        @refresh-profiles="loadProfiles"
      />

      <OperationJobTable
        :jobs="jobs"
        :databases="databases"
        @refresh-job="refreshJob"
        @view-job-detail="viewJobDetail"
      />
    </el-card>
  </section>
</template>
