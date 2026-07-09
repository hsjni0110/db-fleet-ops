<script setup lang="ts">
import { useRoute } from "vue-router";

const route = useRoute();
const alertId = route.params.alertId;
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Alert Detail #{{ alertId }}</span>
          <el-tag type="danger">Runbook</el-tag>
        </div>
      </template>

      <p class="page-description">
        Alert 상세, observed value, threshold value, runbook key, 처리 이력을 표시할 예정입니다.
      </p>
    </el-card>
  </section>
</template>