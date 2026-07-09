<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Agents</span>
          <el-tag type="info">Internal API 기반</el-tag>
        </div>
      </template>

      <p class="page-description">
        Agent 등록, Heartbeat, Task Polling API는 현재 internal API입니다.
        운영 콘솔에서는 Agent 조회용 public API가 필요하므로, 이후 백엔드 API 상태에 맞춰 연결합니다.
      </p>

      <el-empty description="Agent overview will be connected when public query API is available." />
    </el-card>
  </section>
</template>