<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { Plus, Refresh } from "@element-plus/icons-vue";

import { operationalLabel } from "../labels";
import FieldHelp from "./FieldHelp.vue";
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
  refreshProfiles: [];
}>();

const jobTypes = ["BACKUP", "CONFIGURATION_CHECK", "CONFIGURATION_APPLY"] as const;
const jobTypeOptions = jobTypes.map((value) => ({ label: operationalLabel(value), value }));
const selectedJobType = ref<(typeof jobTypes)[number]>("BACKUP");

const jobForm = reactive<OperationJobFormState>({
  databaseId: null,
  profileId: null,
  reason: "콘솔 수동 작업",
  requestedBy: "local-user",
  parametersText: "",
});

const activeProfiles = computed(() => {
  return props.profiles.filter((profile) => profile.status === "ACTIVE");
});

const selectedProfile = computed(() => {
  return activeProfiles.value.find((profile) => profile.profileId === jobForm.profileId);
});

const isConfigurationJob = computed(() => {
  return selectedJobType.value === "CONFIGURATION_CHECK" ||
    selectedJobType.value === "CONFIGURATION_APPLY";
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
  }
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
      <el-form-item label="작업 유형">
        <el-segmented
          v-model="selectedJobType"
          :options="jobTypeOptions"
        />
      </el-form-item>

      <div class="operation-job-form-grid">
        <el-form-item label="대상 데이터베이스" required>
          <el-select
            v-model="jobForm.databaseId"
            class="full-width"
            placeholder="데이터베이스 선택"
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

        <el-form-item v-if="isConfigurationJob" label="설정 기준" required>
          <el-select
            v-model="jobForm.profileId"
            class="full-width"
            placeholder="설정 기준 선택"
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

        <el-form-item label="요청자" required>
          <el-input v-model="jobForm.requestedBy" placeholder="local-user" />
        </el-form-item>

        <el-form-item label="작업 사유" required>
          <el-input
            v-model="jobForm.reason"
            placeholder="예: 정기 백업"
          />
        </el-form-item>

      </div>

      <div v-if="selectedJobType === 'CONFIGURATION_APPLY'" class="setting-values">
        <label for="job-setting-values">적용할 설정값</label>
        <FieldHelp label="적용할 설정값" text="한 줄에 항목=값 한 쌍 입력. 재시작 없이 변경 가능한 허용 항목만 적용." />
        <el-input
          id="job-setting-values"
          v-model="jobForm.parametersText"
          type="textarea"
          :rows="4"
          aria-required="true"
          placeholder="slow_query_log=ON"
        />
      </div>

      <div v-if="isConfigurationJob" class="operation-job-secondary-actions">
        <el-button
          :icon="Refresh"
          :loading="loadingProfiles"
          @click="emit('refreshProfiles')"
        >
          설정 기준 새로고침
        </el-button>
      </div>

      <div class="form-submit">
        <el-button
          type="primary"
          :icon="Plus"
          :loading="creatingJob"
          :disabled="activeDatabases.length === 0 || (isConfigurationJob && !activeProfiles.length)"
          @click="submitOperationJob"
        >
          {{ operationalLabel(selectedJobType) }} 요청
        </el-button>
      </div>
    </el-form>
    <OperationJobLookup
      :looking-up-job="lookingUpJob"
      @lookup-job="emit('lookupJob', $event)"
    />
  </div>
</template>
