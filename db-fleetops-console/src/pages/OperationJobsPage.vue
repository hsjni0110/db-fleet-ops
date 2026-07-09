<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Operation Jobs</span>
          <el-tag type="warning">GET /api/v1/jobs</el-tag>
        </div>
      </template>

      <p class="page-description">
        백업, 설정 점검, 설정 적용 등 비동기 운영 Job의 상태를 추적하는 화면입니다.
        현재 API 명세에는 상세 조회와 Backup Job 생성이 있으므로, 목록 API가 없으면
        백엔드 보완 또는 Dashboard Summary API가 필요합니다.
      </p>

      <el-empty description="Operation job table will be implemented after API client setup." />
    </el-card>
  </section>
</template>