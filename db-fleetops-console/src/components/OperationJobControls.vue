<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { Plus, Refresh } from "@element-plus/icons-vue";

import OperationJobLookup from "./OperationJobLookup.vue";

import type {
  BackupJobRequest,
  ConfigurationApplyParameterRequest,
  ConfigurationProfileResponse,
  CreateConfigurationApplyJobRequest,
  CreateConfigurationCheckJobRequest,
  DatabaseInstanceSummary,
  OperationJobType,
} from "../types";

interface OperationJobFormState {
  databaseId: number | null;
  profileId: number | null;
  reason: string;
  requestedBy: string;
  parametersText: string;
}

const props = defineProps<{
  activeDatabases: DatabaseInstanceSummary[];
  profiles: ConfigurationProfileResponse[];
  loadingDatabases: boolean;
  loadingProfiles: boolean;
  creatingJob: boolean;
  lookingUpJob: boolean;
}>();

const emit = defineEmits<{
  createOperationJob: [
    jobType: OperationJobType,
    databaseId: number | null,
    request:
      | BackupJobRequest
      | CreateConfigurationCheckJobRequest
      | CreateConfigurationApplyJobRequest,
  ];
  lookupJob: [jobId: string];
  refreshDatabases: [];
  refreshProfiles: [];
}>();

const selectedJobType = ref<OperationJobType>("BACKUP");

const jobForm = reactive<OperationJobFormState>({
  databaseId: null,
  profileId: null,
  reason: "manual backup from operation console",
  requestedBy: "local-user",
  parametersText: "",
});

const activeProfiles = computed(() => {
  return props.profiles.filter((profile) => profile.status === "ACTIVE");
});

const selectedProfile = computed(() => {
  return activeProfiles.value.find((profile) => profile.profileId === jobForm.profileId);
});

const selectedJobDescription = computed(() => {
  if (selectedJobType.value === "BACKUP") {
    return "대상 DB의 논리 백업 Job을 생성합니다.";
  }

  if (selectedJobType.value === "CONFIGURATION_CHECK") {
    return "대상 DB의 실제 설정을 Profile 기준으로 점검하는 Job을 생성합니다.";
  }

  if (selectedJobType.value === "CONFIGURATION_APPLY") {
    return "Profile과 입력한 Parameter 값을 기준으로 설정 적용 Job을 생성합니다.";
  }

  return "RESTART Job은 Operation Job 타입에 포함되어 있지만 현재 생성 API는 없습니다.";
});

const isConfigurationJob = computed(() => {
  return selectedJobType.value === "CONFIGURATION_CHECK" ||
    selectedJobType.value === "CONFIGURATION_APPLY";
});

const isRestartJob = computed(() => {
  return selectedJobType.value === "RESTART";
});

function parseApplyParameters() {
  const parameters: ConfigurationApplyParameterRequest[] = [];

  for (const rawLine of jobForm.parametersText.split("\n")) {
    const line = rawLine.trim();

    if (!line) {
      continue;
    }

    const separatorIndex = line.indexOf("=");

    if (separatorIndex <= 0 || separatorIndex === line.length - 1) {
      return null;
    }

    parameters.push({
      parameterName: line.slice(0, separatorIndex).trim(),
      targetValue: line.slice(separatorIndex + 1).trim(),
    });
  }

  return parameters;
}

function buildApplyParametersText(profile: ConfigurationProfileResponse | undefined) {
  if (!profile) {
    return "";
  }

  return profile.parameters
    .filter((parameter) => parameter.dynamic && parameter.applyAllowed)
    .map((parameter) => `${parameter.parameterName}=${parameter.expectedValue}`)
    .join("\n");
}

function submitOperationJob() {
  if (selectedJobType.value === "BACKUP") {
    emit("createOperationJob", selectedJobType.value, jobForm.databaseId, {
      reason: jobForm.reason,
      requestedBy: jobForm.requestedBy,
    });
    return;
  }

  if (selectedJobType.value === "CONFIGURATION_CHECK") {
    emit("createOperationJob", selectedJobType.value, jobForm.databaseId, {
      profileId: Number(jobForm.profileId),
      reason: jobForm.reason,
      requestedBy: jobForm.requestedBy,
    });
    return;
  }

  if (selectedJobType.value === "CONFIGURATION_APPLY") {
    const parameters = parseApplyParameters();

    if (!parameters || parameters.length === 0) {
      emit("createOperationJob", selectedJobType.value, jobForm.databaseId, {
        profileId: Number(jobForm.profileId),
        reason: jobForm.reason,
        requestedBy: jobForm.requestedBy,
        parameters: [],
      });
      return;
    }

    emit("createOperationJob", selectedJobType.value, jobForm.databaseId, {
      profileId: Number(jobForm.profileId),
      reason: jobForm.reason,
      requestedBy: jobForm.requestedBy,
      parameters,
    });
    return;
  }

  emit("createOperationJob", selectedJobType.value, jobForm.databaseId, {
    reason: jobForm.reason,
    requestedBy: jobForm.requestedBy,
  });
}

watch(
  () => props.activeDatabases,
  (activeDatabases) => {
    if (!jobForm.databaseId && activeDatabases.length > 0) {
      jobForm.databaseId = activeDatabases[0].id;
    }
  },
);

watch(
  activeProfiles,
  (profiles) => {
    if (!jobForm.profileId && profiles.length > 0) {
      jobForm.profileId = profiles[0].profileId;
    }
  },
);

watch(
  selectedProfile,
  (profile) => {
    jobForm.parametersText = buildApplyParametersText(profile);
  },
  { immediate: true },
);
</script>

<template>
  <div class="operation-job-controls">
    <el-form label-position="top" class="operation-job-form" @submit.prevent>
      <el-form-item label="Job Type">
        <el-segmented
          v-model="selectedJobType"
          :options="[
            { label: 'Backup', value: 'BACKUP' },
            { label: 'Config Check', value: 'CONFIGURATION_CHECK' },
            { label: 'Config Apply', value: 'CONFIGURATION_APPLY' },
            { label: 'Restart', value: 'RESTART' },
          ]"
        />
      </el-form-item>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        class="operation-job-type-alert"
      >
        <template #title>{{ selectedJobType }}</template>
        <p>{{ selectedJobDescription }}</p>
      </el-alert>

      <div class="operation-job-form-grid">
        <el-form-item label="Target Database" required>
          <el-select
            v-model="jobForm.databaseId"
            class="full-width"
            placeholder="DB 선택"
            :loading="loadingDatabases"
            :disabled="activeDatabases.length === 0"
          >
            <el-option
              v-for="database in activeDatabases"
              :key="database.id"
              :label="`${database.name} (#${database.id})`"
              :value="database.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="isConfigurationJob" label="Configuration Profile" required>
          <el-select
            v-model="jobForm.profileId"
            class="full-width"
            placeholder="Profile 선택"
            :loading="loadingProfiles"
            :disabled="activeProfiles.length === 0"
          >
            <el-option
              v-for="profile in activeProfiles"
              :key="profile.profileId"
              :label="`${profile.profileName} (#${profile.profileId})`"
              :value="profile.profileId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Requested By" required>
          <el-input v-model="jobForm.requestedBy" placeholder="local-user" />
        </el-form-item>

        <el-form-item label="Reason" required>
          <el-input
            v-model="jobForm.reason"
            placeholder="operation reason"
          />
        </el-form-item>

        <el-form-item label="Action">
          <el-button
            type="primary"
            :icon="Plus"
            :loading="creatingJob"
            :disabled="activeDatabases.length === 0 || isRestartJob"
            @click="submitOperationJob"
          >
            Create Job
          </el-button>
        </el-form-item>
      </div>

      <el-form-item
        v-if="selectedJobType === 'CONFIGURATION_APPLY'"
        label="Apply Parameters"
        required
      >
        <el-input
          v-model="jobForm.parametersText"
          type="textarea"
          :rows="4"
          placeholder="slow_query_log=ON"
        />
      </el-form-item>

      <div v-if="isConfigurationJob" class="operation-job-secondary-actions">
        <el-button
          :icon="Refresh"
          :loading="loadingProfiles"
          @click="emit('refreshProfiles')"
        >
          Refresh Profiles
        </el-button>
      </div>
    </el-form>
    <OperationJobLookup
      :loading-databases="loadingDatabases"
      :looking-up-job="lookingUpJob"
      @lookup-job="emit('lookupJob', $event)"
      @refresh-databases="emit('refreshDatabases')"
    />
  </div>
</template>
