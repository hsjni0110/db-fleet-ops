<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Alerts</span>
          <el-tag type="danger">Ack / Resolve</el-tag>
        </div>
      </template>

      <p class="page-description">
        Alert 목록을 조회하고 Ack / Resolve 처리하는 화면입니다.
        Alert API 명세가 정리되면 API Client와 연결합니다.
      </p>

      <el-empty description="Alert management page will be implemented after API client setup." />
    </el-card>
  </section>
</template>