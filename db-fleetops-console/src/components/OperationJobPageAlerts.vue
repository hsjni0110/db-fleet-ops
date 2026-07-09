<script setup lang="ts">
defineProps<{
  databasesError: string | null;
  profilesError: string | null;
}>();
</script>

<template>
  <el-alert
    v-if="databasesError"
    type="error"
    show-icon
    :closable="false"
    class="page-alert"
  >
    <template #title>Database Inventory API 호출 실패</template>
    <p>{{ databasesError }}</p>
  </el-alert>

  <el-alert
    v-if="profilesError"
    type="error"
    show-icon
    :closable="false"
    class="page-alert"
  >
    <template #title>Configuration Profile API 호출 실패</template>
    <p>{{ profilesError }}</p>
  </el-alert>

  <el-alert type="info" show-icon :closable="false" class="page-alert">
    <template #title>Operation Job 조회 범위</template>
    <p>
      현재 public API는 Backup, Configuration Check, Configuration Apply 생성과
      Job ID 기반 상세 조회를 제공합니다. 전체 Job 목록 API가 추가되기 전까지
      이 화면은 현재 세션에서 생성하거나 조회한 Job을 추적합니다.
    </p>
  </el-alert>
</template>
