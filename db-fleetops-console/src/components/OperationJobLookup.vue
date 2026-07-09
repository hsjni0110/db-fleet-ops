<script setup lang="ts">
import { ref } from "vue";
import {
  Refresh,
  Search,
} from "@element-plus/icons-vue";

defineProps<{
  loadingDatabases: boolean;
  lookingUpJob: boolean;
}>();

const emit = defineEmits<{
  lookupJob: [jobId: string];
  refreshDatabases: [];
}>();

const lookupJobId = ref("");

function lookupJob() {
  emit("lookupJob", lookupJobId.value);
}
</script>

<template>
  <el-form label-position="top" class="operation-job-lookup" @submit.prevent>
    <div class="operation-job-lookup-row">
      <el-form-item label="Find Job By ID">
        <el-input
          v-model="lookupJobId"
          placeholder="예: 1"
          clearable
          @keyup.enter="lookupJob"
        />
      </el-form-item>

      <div class="operation-job-lookup-actions">
        <el-button
          type="success"
          :icon="Search"
          :loading="lookingUpJob"
          @click="lookupJob"
        >
          Lookup
        </el-button>

        <el-button
          :icon="Refresh"
          :loading="loadingDatabases"
          @click="emit('refreshDatabases')"
        >
          Refresh DBs
        </el-button>
      </div>
    </div>
  </el-form>
</template>
