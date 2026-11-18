<script setup lang="ts">
import { operationalLabel } from "../labels";
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
    ElMessage.warning("설정 항목을 추가할 기준을 선택하세요.");
    return;
  }

  resetParameterForm();
  parameterDialogVisible.value = true;
}

function validateProfileForm() {
  if (!profileForm.profileName.trim()) {
    return "기준 이름을 입력하세요.";
  }

  if (!profileForm.engineType) {
    return "엔진을 선택하세요.";
  }

  if (!profileForm.environment.trim()) {
    return "환경을 선택하세요.";
  }

  return null;
}

function validateParameterForm() {
  if (!parameterForm.parameterName.trim()) {
    return "설정 항목 이름을 입력하세요.";
  }

  if (!parameterForm.expectedValue.trim()) {
    return "기준값을 입력하세요.";
  }

  if (!parameterForm.valueType) {
    return "값 유형을 선택하세요.";
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
    ElMessage.error("설정 기준 목록을 불러오지 못했습니다.");
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
    ElMessage.success(`설정 기준 등록 완료 · #${createdProfile.profileId}`);
  } catch (error) {
    ElMessage.error(`설정 기준 생성에 실패했습니다. ${getApiErrorMessage(error)}`);
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
    ElMessage.success(`설정 기준 #${activatedProfile.profileId}을 활성화했습니다.`);
  } catch (error) {
    ElMessage.error(`설정 기준 활성화에 실패했습니다. ${getApiErrorMessage(error)}`);
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
    ElMessage.success(`설정 기준 #${deactivatedProfile.profileId}을 비활성화했습니다.`);
  } catch (error) {
    ElMessage.error(`설정 기준 비활성화에 실패했습니다. ${getApiErrorMessage(error)}`);
  } finally {
    updatingStatus.value = false;
  }
}

async function submitAddParameter() {
  if (!selectedProfile.value) {
    ElMessage.warning("설정 항목을 추가할 기준을 선택하세요.");
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
    ElMessage.success("설정 항목를 추가했습니다.");
  } catch (error) {
    ElMessage.error(`설정 항목 추가에 실패했습니다. ${getApiErrorMessage(error)}`);
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
            <span>설정 기준 목록</span>
          </div>

          <div class="card-actions">
            <el-button type="primary" :icon="Plus" @click="openCreateDialog">
              설정 기준 등록
            </el-button>
            <el-button :icon="Refresh" :loading="loading" @click="loadProfiles">
              새로고침
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

      <el-table scrollbar-tabindex="0"
        v-if="hasProfiles || loading"
        v-loading="loading"
        class="console-table"
        :data="profiles"
        highlight-current-row
        @row-click="selectProfile"
      >
        <el-table-column prop="profileId" label="기준 번호" width="110" />

        <el-table-column label="설정 기준" min-width="240">
          <template #default="{ row }">
            <div class="table-primary-cell">
              <strong>{{ row.profileName }}</strong>
              <small>{{ formatNullable(row.description) }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="engineType" label="엔진" width="120">
          <template #default="{ row }">{{ operationalLabel(row.engineType) }}</template>
        </el-table-column>
        <el-table-column prop="environment" label="환경" width="150">
          <template #default="{ row }">{{ operationalLabel(row.environment) }}</template>
        </el-table-column>
        <el-table-column label="버전 범위" min-width="150">
          <template #default="{ row }">
            {{ formatNullable(row.versionRange) }}
          </template>
        </el-table-column>

        <el-table-column label="상태" width="120">
          <template #default="{ row }">
            <el-tag :type="getProfileStatusTagType(row.status)">
              {{ operationalLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="설정 항목" width="120">
          <template #default="{ row }">
            {{ row.parameters.length }}
          </template>
        </el-table-column>

        <el-table-column label="관리" width="120">
          <template #default="{ row }">
            <el-button
              type="primary"
              plain
              size="small"
              :icon="View"
              @click.stop="selectProfile(row)"
            >
              선택
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-else
        description="등록된 설정 기준 없음"
      />
    </el-card>

    <el-card v-if="selectedProfile" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>{{ selectedProfile.profileName }}</span>
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
              활성화
            </el-button>
            <el-button
              v-if="canDeactivateSelectedProfile"
              type="warning"
              plain
              :icon="CircleClose"
              :loading="updatingStatus"
              @click="deactivateSelectedProfile"
            >
              비활성화
            </el-button>
            <el-tag :type="getProfileStatusTagType(selectedProfile.status)">
              {{ operationalLabel(selectedProfile.status) }}
            </el-tag>
          </div>
        </div>
      </template>

      <div class="detail-summary-grid">
        <div class="summary-tile">
          <span class="summary-label">기준 번호</span>
          <strong>{{ selectedProfile.profileId }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">엔진</span>
          <strong>{{ operationalLabel(selectedProfile.engineType) }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">환경</span>
          <strong>{{ operationalLabel(selectedProfile.environment) }}</strong>
        </div>
        <div class="summary-tile">
          <span class="summary-label">버전 범위</span>
          <strong>{{ formatNullable(selectedProfile.versionRange) }}</strong>
        </div>
      </div>
    </el-card>

    <el-card v-if="selectedProfile" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>설정 기준 항목</span>
          </div>

          <div class="card-actions">
            <el-button type="primary" plain :icon="Plus" @click="openParameterDialog">
              설정 항목 추가
            </el-button>
            <el-tag type="info">{{ selectedProfile.parameters.length }}개</el-tag>
          </div>
        </div>
      </template>

      <el-table scrollbar-tabindex="0"
        class="console-table"
        :data="selectedProfile.parameters"
        empty-text="등록된 설정 항목 없음"
      >
        <el-table-column prop="parameterName" label="설정 항목" min-width="210" />
        <el-table-column prop="expectedValue" label="기준값" min-width="160" show-overflow-tooltip />
        <el-table-column label="값 유형" width="130">
          <template #default="{ row }">
            <el-tag :type="getValueTypeTagType(row.valueType)" effect="light">
              {{ operationalLabel(row.valueType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="필수" width="110">
          <template #default="{ row }">
            <el-tag :type="row.required ? 'success' : 'info'" effect="light">
              {{ row.required ? "필수" : "선택" }}
            </el-tag>
          </template>
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
        <el-table-column prop="description" label="설명" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatNullable(row.description) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="createDialogVisible"
      title="설정 기준 등록"
      width="640px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="기준 이름" required>
          <el-input v-model="profileForm.profileName" placeholder="mysql-production-standard" />
        </el-form-item>

        <el-form-item label="엔진" required>
          <el-select v-model="profileForm.engineType" class="full-width">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="PostgreSQL" value="POSTGRESQL" />
          </el-select>
        </el-form-item>

        <el-form-item label="환경" required>
          <el-select
            v-model="profileForm.environment"
            class="full-width"
            allow-create
            filterable
          >
            <el-option :label="operationalLabel('LOCAL')" value="LOCAL" />
            <el-option :label="operationalLabel('DEV')" value="DEV" />
            <el-option :label="operationalLabel('STAGING')" value="STAGING" />
            <el-option :label="operationalLabel('PRODUCTION')" value="PRODUCTION" />
          </el-select>
        </el-form-item>

        <el-form-item label="버전 범위">
          <el-input v-model="profileForm.versionRange" placeholder=">=8.0" />
        </el-form-item>

        <el-form-item label="설명">
          <el-input
            v-model="profileForm.description"
            type="textarea"
            :rows="3"
            placeholder="예: 운영 DB 설정 기준"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="card-actions">
          <el-button @click="createDialogVisible = false">
            취소
          </el-button>
          <el-button type="primary" :loading="creatingProfile" @click="submitCreateProfile">
            등록
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="parameterDialogVisible"
      title="설정 항목 추가"
      width="680px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="대상 기준">
          <el-input :model-value="selectedProfile?.profileName ?? '-'" disabled />
        </el-form-item>

        <el-form-item label="항목 이름" required>
          <el-input v-model="parameterForm.parameterName" placeholder="slow_query_log" />
        </el-form-item>

        <el-form-item label="기준값" required>
          <el-input v-model="parameterForm.expectedValue" placeholder="ON" />
        </el-form-item>

        <el-form-item label="값 유형" required>
          <el-segmented
            v-model="parameterForm.valueType"
            :options="[
              { label: operationalLabel('STRING'), value: 'STRING' },
              { label: operationalLabel('NUMBER'), value: 'NUMBER' },
              { label: operationalLabel('BOOLEAN'), value: 'BOOLEAN' },
            ]"
          />
        </el-form-item>

        <el-form-item label="변경 정책">
          <el-checkbox v-model="parameterForm.required">
            필수
          </el-checkbox>
          <el-checkbox v-model="parameterForm.dynamic">
            재시작 없이 변경
          </el-checkbox>
          <el-checkbox v-model="parameterForm.applyAllowed">
            변경 허용
          </el-checkbox>
        </el-form-item>

        <el-form-item label="설명">
          <el-input
            v-model="parameterForm.description"
            type="textarea"
            :rows="3"
            placeholder="예: 느린 쿼리 기록 설정"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="card-actions">
          <el-button @click="parameterDialogVisible = false">
            취소
          </el-button>
          <el-button type="primary" :loading="addingParameter" @click="submitAddParameter">
            설정 항목 추가
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>
