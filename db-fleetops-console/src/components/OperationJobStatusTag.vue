<script setup lang="ts">
import type { OperationJobStatus } from "../types";

defineProps<{
  status: OperationJobStatus;
}>();

function getStatusTagType(status: OperationJobStatus) {
  if (status === "SUCCEEDED") {
    return "success";
  }

  if (status === "QUEUED" || status === "RUNNING") {
    return "warning";
  }

  if (status === "FAILED" || status === "TIMED_OUT") {
    return "danger";
  }

  return "info";
}
</script>

<template>
  <el-tag :type="getStatusTagType(status)" effect="light">
    {{ status }}
  </el-tag>
</template>
