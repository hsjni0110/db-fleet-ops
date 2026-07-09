<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Database Inventory</span>
          <el-tag type="success">GET /api/v1/database-instances</el-tag>
        </div>
      </template>

      <p class="page-description">
        등록된 DB 인스턴스 목록을 표시합니다. Engine, Host, Port, Environment,
        Status를 기준으로 운영자가 관리 대상 DB를 확인할 수 있게 구성합니다.
      </p>

      <el-empty description="Database table will be implemented in the next API integration step." />
    </el-card>
  </section>
</template>