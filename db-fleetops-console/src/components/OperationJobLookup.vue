<script setup lang="ts">
import { ref } from "vue";
import { Search } from "@element-plus/icons-vue";

defineProps<{
  lookingUpJob: boolean;
}>();

const emit = defineEmits<{
  lookupJob: [jobId: string];
}>();

const lookupJobId = ref("");

function lookupJob() {
  emit("lookupJob", lookupJobId.value);
}
</script>

<template>
  <el-form label-position="top" class="operation-job-lookup" @submit.prevent>
    <div class="operation-job-lookup-row">
      <el-form-item label="작업 번호">
        <el-input
          v-model="lookupJobId"
          placeholder="예: 1"
          clearable
          @keyup.enter="lookupJob"
        />
      </el-form-item>

      <div class="operation-job-lookup-actions">
        <el-button
          :icon="Search"
          :loading="lookingUpJob"
          @click="lookupJob"
        >
          조회
        </el-button>
      </div>
    </div>
  </el-form>
</template>
