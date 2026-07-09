<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Configuration Profiles</span>
          <el-tag type="primary">GET /api/v1/configuration-profiles</el-tag>
        </div>
      </template>

      <p class="page-description">
        Desired State Profile을 관리하는 화면입니다. Profile 생성, 활성화, 비활성화,
        Parameter 추가 및 목록 조회를 연결할 예정입니다.
      </p>

      <el-empty description="Configuration profile table will be implemented after API client setup." />
    </el-card>
  </section>
</template>