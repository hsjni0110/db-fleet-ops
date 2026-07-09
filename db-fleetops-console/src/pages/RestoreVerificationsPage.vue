<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Restore Verifications</span>
          <el-tag type="success">Backup 검증</el-tag>
        </div>
      </template>

      <p class="page-description">
        백업 파일이 실제로 복원 가능한지 검증한 결과를 조회하는 화면입니다.
        Job 기준, DB 기준 최신 결과, 검증 상세 API를 연결할 예정입니다.
      </p>

      <el-empty description="Restore verification table will be implemented after API client setup." />
    </el-card>
  </section>
</template>