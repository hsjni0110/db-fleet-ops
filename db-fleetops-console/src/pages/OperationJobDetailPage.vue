<script setup lang="ts">
import { useRoute } from "vue-router";

const route = useRoute();
const jobId = route.params.jobId;
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Operation Job Detail #{{ jobId }}</span>
          <el-tag type="success">GET /api/v1/jobs/{jobId}</el-tag>
        </div>
      </template>

      <p class="page-description">
        Job 상세, Task 결과, Configuration Apply 결과, Restore Verification 결과를
        탭으로 확인하는 화면입니다.
      </p>

      <el-tabs>
        <el-tab-pane label="Overview">Job 기본 정보를 표시할 예정입니다.</el-tab-pane>
        <el-tab-pane label="Tasks">OperationTask 목록을 표시할 예정입니다.</el-tab-pane>
        <el-tab-pane label="Result">결과 Payload와 실패 사유를 표시할 예정입니다.</el-tab-pane>
        <el-tab-pane label="Restore Verification">복원 검증 결과를 표시할 예정입니다.</el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>