<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { CircleCheck, CircleClose, Plus, Refresh, View } from "@element-plus/icons-vue";

import {
  activateConfigurationProfile,
  addConfigurationProfileParameter,
  createConfigurationProfile,
  deactivateConfigurationProfile,
  getApiErrorMessage,
  getConfigurationProfile,
  getConfigurationProfiles,
} from "../api";
import type {
  AddConfigurationProfileParameterRequest,
  ConfigurationProfileResponse,
  ConfigurationProfileStatus,
  CreateConfigurationProfileRequest,
  ParameterValueType,
} from "../types";

const profiles = ref<ConfigurationProfileResponse[]>([]);
const selectedProfileId = ref<number | null>(null);
const loading = ref(false);
const errorMessage = ref<string | null>(null);
const createDialogVisible = ref(false);
const creatingProfile = ref(false);
const parameterDialogVisible = ref(false);
const addingParameter = ref(false);
const updatingStatus = ref(false);

const profileForm = reactive<CreateConfigurationProfileRequest>({
  profileName: "",
  engineType: "MYSQL",
  environment: "LOCAL",
  versionRange: "",
  description: "",
});

const parameterForm = reactive<AddConfigurationProfileParameterRequest>({
  parameterName: "",
  expectedValue: "",
  valueType: "STRING",
  required: true,
  dynamic: true,
  applyAllowed: true,
  description: "",
});

const hasProfiles = computed(() => profiles.value.length > 0);

const selectedProfile = computed(() => {
  if (!selectedProfileId.value) {
    return profiles.value[0] ?? null;
  }

  return profiles.value.find((profile) => profile.profileId === selectedProfileId.value) ?? null;
});

const canActivateSelectedProfile = computed(() => {
  return selectedProfile.value?.status === "DRAFT" || selectedProfile.value?.status === "INACTIVE";
});

const canDeactivateSelectedProfile = computed(() => {
  return selectedProfile.value?.status === "ACTIVE";
});

function getProfileStatusTagType(status: ConfigurationProfileStatus) {
  if (status === "ACTIVE") {
    return "success";
  }

  if (status === "INACTIVE") {
    return "info";
  }

  return "warning";
}

function getValueTypeTagType(valueType: ParameterValueType) {
  if (valueType === "BOOLEAN") {
    return "success";
  }

  if (valueType === "NUMBER") {
    return "warning";
  }

  return "info";
}

function formatNullable(value?: string | number | null) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return String(value);
}

function upsertProfile(profile: ConfigurationProfileResponse) {
  const existingIndex = profiles.value.findIndex((item) => item.profileId === profile.profileId);

  if (existingIndex >= 0) {
    profiles.value.splice(existingIndex, 1, profile);
    return;
  }

  profiles.value = [profile, ...profiles.value];
}

function resetProfileForm() {
  profileForm.profileName = "";
  profileForm.engineType = "MYSQL";
  profileForm.environment = "LOCAL";
  profileForm.versionRange = "";
  profileForm.description = "";
}

function resetParameterForm() {
  parameterForm.parameterName = "";
  parameterForm.expectedValue = "";
  parameterForm.valueType = "STRING";
  parameterForm.required = true;
  parameterForm.dynamic = true;
  parameterForm.applyAllowed = true;
  parameterForm.description = "";
}

function openCreateDialog() {
  resetProfileForm();
  createDialogVisible.value = true;
}

function openParameterDialog() {
  if (!selectedProfile.value) {
    ElMessage.warning("Parameter를 추가할 Profile을 선택하세요.");
    return;
  }

  resetParameterForm();
  parameterDialogVisible.value = true;
}

function validateProfileForm() {
  if (!profileForm.profileName.trim()) {
    return "Profile name is required.";
  }

  if (!profileForm.engineType) {
    return "Engine type is required.";
  }

  if (!profileForm.environment.trim()) {
    return "Environment is required.";
  }

  return null;
}

function validateParameterForm() {
  if (!parameterForm.parameterName.trim()) {
    return "Parameter name is required.";
  }

  if (!parameterForm.expectedValue.trim()) {
    return "Expected value is required.";
  }

  if (!parameterForm.valueType) {
    return "Value type is required.";
  }

  return null;
}

function buildProfileRequest(): CreateConfigurationProfileRequest {
  return {
    profileName: profileForm.profileName.trim(),
    engineType: profileForm.engineType,
    environment: profileForm.environment.trim(),
    versionRange: profileForm.versionRange?.trim() || undefined,
    description: profileForm.description?.trim() || undefined,
  };
}

function buildParameterRequest(): AddConfigurationProfileParameterRequest {
  return {
    parameterName: parameterForm.parameterName.trim(),
    expectedValue: parameterForm.expectedValue.trim(),
    valueType: parameterForm.valueType,
    required: parameterForm.required,
    dynamic: parameterForm.dynamic,
    applyAllowed: parameterForm.applyAllowed,
    description: parameterForm.description?.trim() || undefined,
  };
}

async function loadProfiles() {
  loading.value = true;
  errorMessage.value = null;

  try {
    profiles.value = await getConfigurationProfiles();

    if (
      profiles.value.length > 0 &&
      !profiles.value.some((profile) => profile.profileId === selectedProfileId.value)
    ) {
      selectedProfileId.value = profiles.value[0].profileId;
    }
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error);
    ElMessage.error("Configuration Profile 목록을 불러오지 못했습니다.");
  } finally {
    loading.value = false;
  }
}

function selectProfile(profile: ConfigurationProfileResponse) {
  selectedProfileId.value = profile.profileId;
}

async function refreshProfile(profileId: number) {
  const profile = await getConfigurationProfile(profileId);

  upsertProfile(profile);
  selectedProfileId.value = profile.profileId;
}

async function submitCreateProfile() {
  const validationMessage = validateProfileForm();

  if (validationMessage) {
    ElMessage.warning(validationMessage);
    return;
  }

  creatingProfile.value = true;

  try {
    const createdProfile = await createConfigurationProfile(buildProfileRequest());

    upsertProfile(createdProfile);
    selectedProfileId.value = createdProfile.profileId;
    createDialogVisible.value = false;
    ElMessage.success(`Configuration Profile을 생성했습니다. profileId=${createdProfile.profileId}`);
  } catch (error) {
    ElMessage.error(`Configuration Profile 생성에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    creatingProfile.value = false;
  }
}

async function activateSelectedProfile() {
  if (!selectedProfile.value) {
    return;
  }

  updatingStatus.value = true;

  try {
    const activatedProfile = await activateConfigurationProfile(selectedProfile.value.profileId);

    upsertProfile(activatedProfile);
    selectedProfileId.value = activatedProfile.profileId;
    ElMessage.success(`Profile #${activatedProfile.profileId}을 활성화했습니다.`);
  } catch (error) {
    ElMessage.error(`Profile 활성화에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    updatingStatus.value = false;
  }
}

async function deactivateSelectedProfile() {
  if (!selectedProfile.value) {
    return;
  }

  updatingStatus.value = true;

  try {
    const deactivatedProfile = await deactivateConfigurationProfile(selectedProfile.value.profileId);

    upsertProfile(deactivatedProfile);
    selectedProfileId.value = deactivatedProfile.profileId;
    ElMessage.success(`Profile #${deactivatedProfile.profileId}을 비활성화했습니다.`);
  } catch (error) {
    ElMessage.error(`Profile 비활성화에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    updatingStatus.value = false;
  }
}

async function submitAddParameter() {
  if (!selectedProfile.value) {
    ElMessage.warning("Parameter를 추가할 Profile을 선택하세요.");
    return;
  }

  const validationMessage = validateParameterForm();

  if (validationMessage) {
    ElMessage.warning(validationMessage);
    return;
  }

  addingParameter.value = true;

  try {
    await addConfigurationProfileParameter(
      selectedProfile.value.profileId,
      buildParameterRequest(),
    );

    await refreshProfile(selectedProfile.value.profileId);
    parameterDialogVisible.value = false;
    ElMessage.success("Profile Parameter를 추가했습니다.");
  } catch (error) {
    ElMessage.error(`Profile Parameter 추가에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    addingParameter.value = false;
  }
}

onMounted(loadProfiles);
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>Configuration Profiles</span>
            <p class="card-subtitle">DB가 따라야 할 Desired State 기준값을 확인합니다.</p>
          </div>

          <div class="card-actions">
            <el-button type="primary" :icon="Plus" @click="openCreateDialog">
              Create Profile
            </el-button>
            <el-button :icon="Refresh" :loading="loading" @click="loadProfiles">
              Refresh
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="errorMessage"
        class="page-alert"
        type="error"
        :title="errorMessage"
        show-icon
        :closable="false"
      />

      <el-table
        v-if="hasProfiles || loading"
        v-loading="loading"
        class="console-table"
        :data="profiles"
        highlight-current-row
        @row-click="selectProfile"
      >
        <el-table-column prop="profileId" label="Profile ID" width="110" />

        <el-table-column label="Profile" min-width="240">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.profileName }}</strong>
              <small>{{ formatNullable(row.description) }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="engineType" label="Engine" width="120" />
        <el-table-column prop="environment" label="Environment" width="150" />
        <el-table-column label="Version Range" min-width="150">
          <template #default="{ row }">
            {{ formatNullable(row.versionRange) }}
          </template>
        </el-table-column>

        <el-table-column label="Status" width="120">
          <template #default="{ row }">
            <el-tag :type="getProfileStatusTagType(row.status)">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Parameters" width="120">
          <template #default="{ row }">
            {{ row.parameters.length }}
          </template>
        </el-table-column>

        <el-table-column label="Actions" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              plain
              size="small"
              :icon="View"
              @click.stop="selectProfile(row)"
            >
              Select
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-else
        description="No configuration profiles registered yet."
      />
    </el-card>

    <el-card v-if="selectedProfile" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>{{ selectedProfile.profileName }}</span>
            <p class="card-subtitle">Profile 기준값과 Parameter 정책을 확인합니다.</p>
          </div>

          <div class="card-actions">
            <el-button
              v-if="canActivateSelectedProfile"
              type="success"
              plain
              :icon="CircleCheck"
              :loading="updatingStatus"
              @click="activateSelectedProfile"
            >
              Activate
            </el-button>
            <el-button
              v-if="canDeactivateSelectedProfile"
              type="warning"
              plain
              :icon="CircleClose"
              :loading="updatingStatus"
              @click="deactivateSelectedProfile"
            >
              Deactivate
            </el-button>
            <el-tag :type="getProfileStatusTagType(selectedProfile.status)">
              {{ selectedProfile.status }}
            </el-tag>
          </div>
        </div>
      </template>

      <div class="detail-summary-grid">
        <div class="summary-tile">
          <span class="summary-label">Profile ID</span>
          <strong>{{ selectedProfile.profileId }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Engine</span>
          <strong>{{ selectedProfile.engineType }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Environment</span>
          <strong>{{ selectedProfile.environment }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">Version Range</span>
          <strong>{{ formatNullable(selectedProfile.versionRange) }}</strong>
        </div>
      </div>
    </el-card>

    <el-card v-if="selectedProfile" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>Profile Parameters</span>
            <p class="card-subtitle">Expected value와 Apply 가능 정책을 확인합니다.</p>
          </div>

          <div class="card-actions">
            <el-button type="primary" plain :icon="Plus" @click="openParameterDialog">
              Add Parameter
            </el-button>
            <el-tag type="info">{{ selectedProfile.parameters.length }} items</el-tag>
          </div>
        </div>
      </template>

      <el-table
        class="console-table"
        :data="selectedProfile.parameters"
        empty-text="No parameters registered for this profile."
      >
        <el-table-column prop="parameterName" label="Parameter" min-width="210" />
        <el-table-column prop="expectedValue" label="Expected Value" min-width="160" show-overflow-tooltip />
        <el-table-column label="Value Type" width="130">
          <template #default="{ row }">
            <el-tag :type="getValueTypeTagType(row.valueType)" effect="light">
              {{ row.valueType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Required" width="110">
          <template #default="{ row }">
            <el-tag :type="row.required ? 'success' : 'info'" effect="light">
              {{ row.required ? "YES" : "NO" }}
            </el-tag>
          </template>
        </el-table-column>
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
        <el-table-column prop="description" label="Description" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatNullable(row.description) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="createDialogVisible"
      title="Create Configuration Profile"
      width="640px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="Profile Name" required>
          <el-input v-model="profileForm.profileName" placeholder="mysql-production-standard" />
        </el-form-item>

        <el-form-item label="Engine Type" required>
          <el-select v-model="profileForm.engineType" class="full-width">
            <el-option label="MYSQL" value="MYSQL" />
            <el-option label="POSTGRESQL" value="POSTGRESQL" />
          </el-select>
        </el-form-item>

        <el-form-item label="Environment" required>
          <el-select
            v-model="profileForm.environment"
            class="full-width"
            allow-create
            filterable
          >
            <el-option label="LOCAL" value="LOCAL" />
            <el-option label="DEV" value="DEV" />
            <el-option label="STAGING" value="STAGING" />
            <el-option label="PRODUCTION" value="PRODUCTION" />
          </el-select>
        </el-form-item>

        <el-form-item label="Version Range">
          <el-input v-model="profileForm.versionRange" placeholder=">=8.0" />
        </el-form-item>

        <el-form-item label="Description">
          <el-input
            v-model="profileForm.description"
            type="textarea"
            :rows="3"
            placeholder="MySQL production baseline profile"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="card-actions">
          <el-button @click="createDialogVisible = false">
            Cancel
          </el-button>
          <el-button type="primary" :loading="creatingProfile" @click="submitCreateProfile">
            Create
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="parameterDialogVisible"
      title="Add Profile Parameter"
      width="680px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="Target Profile">
          <el-input :model-value="selectedProfile?.profileName ?? '-'" disabled />
        </el-form-item>

        <el-form-item label="Parameter Name" required>
          <el-input v-model="parameterForm.parameterName" placeholder="slow_query_log" />
        </el-form-item>

        <el-form-item label="Expected Value" required>
          <el-input v-model="parameterForm.expectedValue" placeholder="ON" />
        </el-form-item>

        <el-form-item label="Value Type" required>
          <el-segmented
            v-model="parameterForm.valueType"
            :options="[
              { label: 'String', value: 'STRING' },
              { label: 'Number', value: 'NUMBER' },
              { label: 'Boolean', value: 'BOOLEAN' },
            ]"
          />
        </el-form-item>

        <el-form-item label="Policy">
          <el-checkbox v-model="parameterForm.required">
            Required
          </el-checkbox>
          <el-checkbox v-model="parameterForm.dynamic">
            Dynamic
          </el-checkbox>
          <el-checkbox v-model="parameterForm.applyAllowed">
            Apply Allowed
          </el-checkbox>
        </el-form-item>

        <el-form-item label="Description">
          <el-input
            v-model="parameterForm.description"
            type="textarea"
            :rows="3"
            placeholder="Production DB should enable slow query log."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="card-actions">
          <el-button @click="parameterDialogVisible = false">
            Cancel
          </el-button>
          <el-button type="primary" :loading="addingParameter" @click="submitAddParameter">
            Add Parameter
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>
