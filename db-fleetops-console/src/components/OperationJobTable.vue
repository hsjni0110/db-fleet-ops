<script setup lang="ts">
import { operationalLabel } from "../labels";
import { Refresh, View } from "@element-plus/icons-vue";

import OperationJobStatusTag from "./OperationJobStatusTag.vue";

import type {
  DatabaseInstanceSummary,
  OperationJobResponse,
} from "../types";

const props = defineProps<{
  jobs: OperationJobResponse[];
  databases: DatabaseInstanceSummary[];
}>();

defineEmits<{
  refreshJob: [job: OperationJobResponse];
  viewJobDetail: [job: OperationJobResponse];
}>();

function getDatabaseLabel(databaseId: number) {
  const database = props.databases.find((item) => item.id === databaseId);

  if (!database) {
    return `#${databaseId}`;
  }

  return `${database.name} (#${database.id})`;
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return "-";
  }

  return value.replace("T", " ");
}
</script>

<template>
  <el-table scrollbar-tabindex="0"
    :data="jobs"
    border
    stripe
    class="console-table"
    empty-text="현재 화면에서 요청·조회한 작업 없음"
  >
    <el-table-column prop="jobId" label="작업" width="100">
      <template #default="{ row }">
        <strong>#{{ row.jobId }}</strong>
      </template>
    </el-table-column>

    <el-table-column prop="jobType" label="유형" width="150">
      <template #default="{ row }">{{ operationalLabel(row.jobType) }}</template>
    </el-table-column>

    <el-table-column prop="status" label="상태" width="140">
      <template #default="{ row }">
        <OperationJobStatusTag :status="row.status" />
      </template>
    </el-table-column>

    <el-table-column label="대상 데이터베이스" min-width="190">
      <template #default="{ row }">
        {{ getDatabaseLabel(row.targetDatabaseId) }}
      </template>
    </el-table-column>

    <el-table-column prop="requestedBy" label="요청자" width="160" />

    <el-table-column label="재시도" width="110">
      <template #default="{ row }">
        {{ row.retryCount }} / {{ row.maxRetryCount }}
      </template>
    </el-table-column>

    <el-table-column label="등록 시각" min-width="170">
      <template #default="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>
    </el-table-column>

    <el-table-column label="관리" width="220">
      <template #default="{ row }">
        <div class="table-actions">
          <el-button
            size="small"
            :icon="Refresh"
            @click="$emit('refreshJob', row)"
          >
            새로고침
          </el-button>

          <el-button
            size="small"
            :icon="View"
            @click="$emit('viewJobDetail', row)"
          >
            상세
          </el-button>
        </div>
      </template>
    </el-table-column>
  </el-table>
</template>
