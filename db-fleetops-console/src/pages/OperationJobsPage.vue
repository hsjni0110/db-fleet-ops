<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";

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
    return "Backup 대상 DB를 선택하세요.";
  }

  if (!request.reason.trim()) {
    return "Backup 사유를 입력하세요.";
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
    return "Configuration Profile을 선택하세요.";
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
    ElMessage.error("Configuration Profile 목록을 불러오지 못했습니다.");
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
    ElMessage.warning("RESTART Job 생성 API는 아직 제공되지 않습니다.");
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
    ElMessage.warning("Apply Parameter는 parameterName=targetValue 형식으로 하나 이상 입력하세요.");
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
    ElMessage.success(`${createdJob.jobType} Job이 생성되었습니다. jobId=${createdJob.jobId}`);
  } catch (error) {
    ElMessage.error(`Operation Job 생성에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    creatingJob.value = false;
  }
}

async function lookupJob(jobId: string) {
  const normalizedJobId = jobId.trim();

  if (!normalizedJobId) {
    ElMessage.warning("조회할 Job ID를 입력하세요.");
    return;
  }

  lookingUpJob.value = true;

  try {
    const job = await getOperationJob(normalizedJobId);

    upsertJob(job);
    ElMessage.success(`Job #${job.jobId} 상태를 조회했습니다.`);
  } catch (error) {
    ElMessage.error(`Job 조회에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    lookingUpJob.value = false;
  }
}

async function refreshJob(job: OperationJobResponse) {
  try {
    const refreshedJob = await getOperationJob(job.jobId);

    upsertJob(refreshedJob);
    ElMessage.success(`Job #${job.jobId} 상태를 갱신했습니다.`);
  } catch (error) {
    ElMessage.error(`Job 갱신에 실패했습니다. ${getApiErrorMessage(error)}`);
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
            <span>Operation Jobs</span>
            <p class="card-subtitle">
              Backup, Configuration Check, Configuration Apply 등 운영 Job을 생성하고 상태를 추적합니다.
            </p>
          </div>

          <div class="card-actions">
            <el-button
              :icon="Refresh"
              :loading="loadingDatabases"
              @click="loadDatabases"
            >
              Refresh DBs
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
        @refresh-databases="loadDatabases"
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
