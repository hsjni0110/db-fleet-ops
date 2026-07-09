<script setup lang="ts">
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
    return `databaseId=${databaseId}`;
  }

  return `${database.name} (#${database.id})`;
}

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
</script>

<template>
  <el-table
    :data="jobs"
    border
    stripe
    class="console-table"
    empty-text="현재 세션에서 생성하거나 조회한 Job이 없습니다."
  >
    <el-table-column prop="jobId" label="Job" width="100">
      <template #default="{ row }">
        <strong>#{{ row.jobId }}</strong>
      </template>
    </el-table-column>

    <el-table-column prop="jobType" label="Type" width="150" />

    <el-table-column prop="status" label="Status" width="140">
      <template #default="{ row }">
        <OperationJobStatusTag :status="row.status" />
      </template>
    </el-table-column>

    <el-table-column label="Target DB" min-width="190">
      <template #default="{ row }">
        {{ getDatabaseLabel(row.targetDatabaseId) }}
      </template>
    </el-table-column>

    <el-table-column prop="requestedBy" label="Requested By" width="160" />

    <el-table-column label="Retry" width="110">
      <template #default="{ row }">
        {{ row.retryCount }} / {{ row.maxRetryCount }}
      </template>
    </el-table-column>

    <el-table-column label="Result" min-width="220">
      <template #default="{ row }">
        <div class="table-primary-cell">
          <strong>{{ formatNullable(row.resultCode) }}</strong>
          <small>{{ formatNullable(row.resultMessage) }}</small>
        </div>
      </template>
    </el-table-column>

    <el-table-column label="Created At" min-width="170">
      <template #default="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>
    </el-table-column>

    <el-table-column label="Actions" fixed="right" width="220">
      <template #default="{ row }">
        <div class="table-actions">
          <el-button
            size="small"
            :icon="Refresh"
            @click="$emit('refreshJob', row)"
          >
            Refresh
          </el-button>

          <el-button
            size="small"
            type="primary"
            :icon="View"
            @click="$emit('viewJobDetail', row)"
          >
            Detail
          </el-button>
        </div>
      </template>
    </el-table-column>
  </el-table>

  <el-empty
    v-if="jobs.length === 0"
    description="Backup Job을 생성하거나 Job ID를 조회하면 이곳에서 상태를 추적할 수 있습니다."
  />
</template>
