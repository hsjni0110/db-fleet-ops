<script setup lang="ts">
import { useRoute } from "vue-router";

const route = useRoute();
const agentId = route.params.agentId;
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Agent Detail #{{ agentId }}</span>
          <el-tag type="info">Heartbeat / Host Metric</el-tag>
        </div>
      </template>

      <p class="page-description">
        Agent 기본 정보, 마지막 Heartbeat, CPU/Memory/Disk 사용률, 최근 실행 Task를 표시할 예정입니다.
      </p>
    </el-card>
  </section>
</template>