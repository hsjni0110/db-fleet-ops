<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Dashboard Overview</span>
          <el-tag type="primary">Phase 12</el-tag>
        </div>
      </template>

      <p class="page-description">
        전체 DB 운영 상태를 요약하는 화면입니다. 이후 Database Inventory, Alert,
        Operation Job, Agent API를 조합해 Summary Card를 구성합니다.
      </p>

      <div class="placeholder-grid">
        <div class="placeholder-card">Total Databases</div>
        <div class="placeholder-card">Healthy Databases</div>
        <div class="placeholder-card">Open Alerts</div>
        <div class="placeholder-card">Running Jobs</div>
      </div>
    </el-card>
  </section>
</template>