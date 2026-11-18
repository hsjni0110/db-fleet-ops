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
    <template #title>데이터베이스 목록 조회 실패</template>
    <p>{{ databasesError }}</p>
  </el-alert>

  <el-alert
    v-if="profilesError"
    type="error"
    show-icon
    :closable="false"
    class="page-alert"
  >
    <template #title>설정 기준 조회 실패</template>
    <p>{{ profilesError }}</p>
  </el-alert>

</template>
