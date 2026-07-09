<script setup lang="ts">
import { useRoute } from "vue-router";

const route = useRoute();
const databaseId = route.params.databaseId;
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Database Detail #{{ databaseId }}</span>
          <el-tag type="primary">Diagnostics</el-tag>
        </div>
      </template>

      <p class="page-description">
        DB 상세 화면입니다. Overview, Health, Sessions, Long Transactions, Lock Waits,
        Slow Queries, Configuration, Drift, Capabilities, Operations 탭으로 확장합니다.
      </p>

      <el-tabs>
        <el-tab-pane label="Overview">기본 정보와 최신 Health를 표시할 예정입니다.</el-tab-pane>
        <el-tab-pane label="Health">Health Check 결과를 표시할 예정입니다.</el-tab-pane>
        <el-tab-pane label="Sessions">Diagnostics Sessions 결과를 표시할 예정입니다.</el-tab-pane>
        <el-tab-pane label="Lock Waits">Lock Wait 분석 결과를 표시할 예정입니다.</el-tab-pane>
        <el-tab-pane label="Slow Queries">Unsupported 기능은 명확한 메시지로 표시할 예정입니다.</el-tab-pane>
        <el-tab-pane label="Drift">Configuration Drift 최신 결과를 표시할 예정입니다.</el-tab-pane>
        <el-tab-pane label="Capabilities">DB 엔진별 지원 기능을 표시할 예정입니다.</el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>